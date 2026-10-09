package org.lolaf.staffix.admin.server.metrics;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricStatistic;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.web.TelemetryProblems;
import org.lolaf.staffix.admin.server.web.TimeRange;
import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleBinaryOperator;

@RestController
@Validated
@RequiredArgsConstructor
public class MetricsController {

    private static final String MESSAGE_TYPE = FixMonitoringAttributes.FIX_MESSAGE_TYPE.getKey();
    private static final String DIRECTION = FixMonitoringAttributes.FIX_MESSAGE_DIRECTION.getKey();
    private static final String READ = "messages.read.latency";
    private static final String WRITE = "messages.write.latency";
    /**
     * staffix registers each message timer for both directions but records it in one only.
     */
    private static final Map<String, String> RECORDED_DIRECTION = Map.of(
            READ, "in",
            "messages.decoding.latency", "in",
            WRITE, "out",
            "messages.encoding.latency", "out");

    private final TelemetrySources telemetrySources;
    private final EngineGateway gateway;

    /**
     * Every meter the scope's running sessions publish, as their engine describes it; a meter several sessions publish
     * once.
     */
    @GetMapping("/api/metrics/meters")
    public List<Meter> getMeters(@RequestParam String engineId,
                                 @RequestParam(required = false) @Nullable String group,
                                 @RequestParam(required = false) @Nullable String sessionId) {
        TelemetryProblems.requireGroupForSession(group, sessionId);
        Map<String, Meter> byName = new LinkedHashMap<>();
        gateway.getMeters(engineId).stream()
                .filter(session -> group == null || session.getGroup().equals(group))
                .filter(session -> sessionId == null || session.getName().equals(sessionId))
                .flatMap(session -> session.getMeters().stream())
                .forEach(meter -> byName.merge(meter.getName(), meter, Meter::merge));
        return List.copyOf(byName.values());
    }

    /**
     * One series per combination of the {@code splitBy} tags' values. A rate is averaged over {@code rateWindow},
     * else the step, widened to hold two of the engines' exports and to the backend's own minimum; the result says to
     * what.
     */
    @GetMapping("/api/metrics")
    public MetricsResult queryMetrics(@RequestParam String engineId,
                                      @RequestParam(required = false) @Nullable String group,
                                      @RequestParam(required = false) @Nullable String sessionId,
                                      @RequestParam String meter,
                                      @RequestParam MetricStatistic statistic,
                                      @RequestParam(defaultValue = "0.99") @DecimalMin("0") @DecimalMax("1") double quantile,
                                      @RequestParam(required = false) @Nullable List<String> splitBy,
                                      @RequestParam(required = false) @Nullable TimeUnit gaugeUnit,
                                      @RequestParam Instant from,
                                      @RequestParam Instant to,
                                      @RequestParam(required = false) @Nullable Duration rateWindow) {
        if (statistic.equals(MetricStatistic.SHARE)) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "A share is read through /api/availability"), null);
        }
        MetricQuery.MetricQueryBuilder query = MetricQuery.builder()
                .meter(meter)
                .statistic(statistic)
                .quantile(statistic == MetricStatistic.QUANTILE ? quantile : null)
                .splitBy(Objects.requireNonNullElse(splitBy, List.of()))
                .gaugeUnit(gaugeUnit);
        if (RECORDED_DIRECTION.containsKey(meter)) {
            query.tagFilter(DIRECTION, RECORDED_DIRECTION.get(meter));
        }
        return query(engineId, group, sessionId, from, to, rateWindow, statistic, List.of(query));
    }

    /**
     * Messages per second, one series per message type and direction: received ones counted by the read timer,
     * sent ones by the write timer.
     */
    @GetMapping("/api/metrics/messages")
    public MetricsResult queryMessageRates(@RequestParam String engineId,
                                           @RequestParam(required = false) @Nullable String group,
                                           @RequestParam(required = false) @Nullable String sessionId,
                                           @RequestParam Instant from,
                                           @RequestParam Instant to,
                                           @RequestParam(required = false) @Nullable Duration rateWindow) {
        return query(engineId, group, sessionId, from, to, rateWindow, MetricStatistic.RATE, List.of(
                messageRate(READ, "in"),
                messageRate(WRITE, "out")));
    }

    private static MetricQuery.MetricQueryBuilder messageRate(String timer, String direction) {
        return MetricQuery.builder()
                .meter(timer)
                .statistic(MetricStatistic.RATE)
                .splitBy(MESSAGE_TYPE)
                .splitBy(DIRECTION)
                .tagFilter(DIRECTION, direction);
    }

    private MetricsResult query(String engineId, @Nullable String group, @Nullable String sessionId, Instant from, Instant to,
                                @Nullable Duration rateWindow, MetricStatistic statistic, List<MetricQuery.MetricQueryBuilder> queries) {
        requirePositive(rateWindow);
        TelemetrySources.Resolved<MetricsSource> resolved = TelemetryProblems.requireSources("metrics", engineId, group, sessionId,
                () -> telemetrySources.metrics(engineId, group, sessionId));
        TimeRange range = TimeRange.of(from, to);
        Duration step = range.chartStep();
        try {
            List<MetricSeries> series = queries.stream()
                    .map(query -> query.engineId(engineId).group(group).sessionId(sessionId)
                            .from(range.getFrom()).to(range.getTo()).step(step).rateWindow(rateWindow).build())
                    .flatMap(query -> resolved.sources().stream().flatMap(source -> source.query(query.toBuilder()
                            .timerBaseUnit(resolved.timerBaseUnits().get(source))
                            .rateWindow(rateWindowFor(query, resolved.exportSteps().get(source)))
                            .build()).stream()))
                    .toList();
            Duration minRateWindow = resolved.sources().stream()
                    .map(source -> longest(source.getMinRateWindow(), twoExports(resolved.exportSteps().get(source))))
                    .max(Comparator.naturalOrder())
                    .orElse(Duration.ZERO);
            return MetricsResult.builder()
                    .series(resolved.sources().size() == 1 ? series : combineSameLabels(series, statistic))
                    .notCollected(resolved.notCollected())
                    .rateWindow(widestRateWindow(series))
                    .minRateWindow(minRateWindow.isZero() ? null : minRateWindow)
                    .step(step)
                    .build();
        } catch (MetricsSourceException e) {
            throw TelemetryProblems.backendFailed("metrics", e);
        }
    }

    private static @Nullable Duration rateWindowFor(MetricQuery query, @Nullable Duration exportStep) {
        if (exportStep == null) {
            return query.getRateWindow();
        }
        return longest(Objects.requireNonNullElse(query.getRateWindow(), query.getStep()), twoExports(exportStep));
    }

    /**
     * A rate needs two samples in its window, and an engine's meters change once an export.
     */
    private static Duration twoExports(@Nullable Duration exportStep) {
        return exportStep == null ? Duration.ZERO : exportStep.multipliedBy(2);
    }

    private static Duration longest(Duration one, Duration other) {
        return one.compareTo(other) >= 0 ? one : other;
    }

    private static void requirePositive(@Nullable Duration rateWindow) {
        if (rateWindow != null && !rateWindow.isPositive()) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "'rateWindow' must be positive"), null);
        }
    }

    /**
     * Backends aggregate over their own sessions only. A rate adds up; anything else cannot be combined, so the
     * highest is kept, an upper bound of the true one.
     */
    private static List<MetricSeries> combineSameLabels(List<MetricSeries> series, MetricStatistic statistic) {
        DoubleBinaryOperator combine = statistic == MetricStatistic.RATE ? Double::sum : Math::max;
        Map<Map<String, String>, List<MetricSeries>> byLabels = new LinkedHashMap<>();
        series.forEach(one -> byLabels.computeIfAbsent(one.getLabels(), labels -> new ArrayList<>()).add(one));
        return byLabels.entrySet().stream()
                .map(entry -> {
                    Map<Instant, Double> points = new TreeMap<>();
                    entry.getValue().forEach(one -> one.getPoints()
                            .forEach(point -> points.merge(point.getTimestamp(), point.getValue(), combine::applyAsDouble)));
                    return MetricSeries.builder()
                            .labels(entry.getKey())
                            .points(points.entrySet().stream()
                                    .map(point -> new MetricPoint(point.getKey(), point.getValue()))
                                    .toList())
                            .rateWindow(widestRateWindow(entry.getValue()))
                            .build();
                })
                .toList();
    }

    private static @Nullable Duration widestRateWindow(List<MetricSeries> series) {
        return series.stream()
                .map(MetricSeries::getRateWindow)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
