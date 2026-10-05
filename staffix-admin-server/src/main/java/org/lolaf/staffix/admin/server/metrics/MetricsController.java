package org.lolaf.staffix.admin.server.metrics;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.lolaf.staffix.admin.server.web.TelemetryProblems;
import org.lolaf.staffix.admin.server.web.TimeRange;
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
import java.util.function.DoubleBinaryOperator;

@RestController
@Validated
@RequiredArgsConstructor
public class MetricsController {

    private final TelemetrySources telemetrySources;

    /**
     * The kinds the monitoring plugins of the scope's sessions measure, so a chart of a meter that is off is not
     * offered; every kind when the engine does not say.
     */
    @GetMapping("/api/metrics/kinds")
    public List<MetricKind> getMetricKinds(@RequestParam String engineId,
                                           @RequestParam(required = false) @Nullable String group,
                                           @RequestParam(required = false) @Nullable String sessionId) {
        TelemetryProblems.requireGroupForSession(group, sessionId);
        return List.copyOf(telemetrySources.metricKinds(engineId, group, sessionId));
    }

    /**
     * Per message type kinds give one series per type and direction, the others one series per session. A rate is
     * averaged over {@code rateWindow}, else the step, widened to hold two of the engines' exports and to the
     * backend's own minimum; the result says to what.
     */
    @GetMapping("/api/metrics")
    public MetricsResult queryMetrics(@RequestParam String engineId,
                                           @RequestParam(required = false) @Nullable String group,
                                           @RequestParam(required = false) @Nullable String sessionId,
                                           @RequestParam MetricKind kind,
                                           @RequestParam(defaultValue = "0.99") @DecimalMin("0") @DecimalMax("1") double quantile,
                                           @RequestParam Instant from,
                                           @RequestParam Instant to,
                                           @RequestParam(required = false) @Nullable Duration rateWindow) {
        TelemetryProblems.requireGroupForSession(group, sessionId);
        requirePositive(rateWindow);
        TelemetrySources.Resolved<MetricsSource> resolved = resolve(engineId, group, sessionId);
        TimeRange range = TimeRange.of(from, to);
        MetricQuery query = MetricQuery.builder()
                .engineId(engineId)
                .group(group)
                .sessionId(sessionId)
                .kind(kind)
                .quantile(kind.isLatency() ? quantile : null)
                .from(range.getFrom())
                .to(range.getTo())
                .step(range.chartStep())
                .rateWindow(rateWindow)
                .build();
        try {
            List<MetricSeries> series = resolved.sources().stream()
                    .flatMap(source -> source.query(query.toBuilder()
                            .timerBaseUnit(resolved.timerBaseUnits().get(source))
                            .rateWindow(rateWindowFor(query, resolved.exportSteps().get(source)))
                            .build()).stream())
                    .toList();
            Duration minRateWindow = resolved.sources().stream()
                    .map(source -> longest(source.getMinRateWindow(), twoExports(resolved.exportSteps().get(source))))
                    .max(Comparator.naturalOrder())
                    .orElse(Duration.ZERO);
            return MetricsResult.builder()
                    .series(resolved.sources().size() == 1 ? series : combineSameLabels(series, kind))
                    .notCollected(resolved.notCollected())
                    .rateWindow(widestRateWindow(series))
                    .minRateWindow(minRateWindow.isZero() ? null : minRateWindow)
                    .step(query.getStep())
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
     * Backends aggregate a per message type kind over their own sessions only. A rate adds up; a quantile cannot be
     * combined, so the highest one is kept, an upper bound of the true one.
     */
    private static List<MetricSeries> combineSameLabels(List<MetricSeries> series, MetricKind kind) {
        DoubleBinaryOperator combine = kind.isLatency() ? Math::max : Double::sum;
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

    private TelemetrySources.Resolved<MetricsSource> resolve(String engineId, @Nullable String group, @Nullable String sessionId) {
        TelemetrySources.Resolved<MetricsSource> resolved;
        try {
            resolved = telemetrySources.metrics(engineId, group, sessionId);
        } catch (IllegalArgumentException e) {
            throw TelemetryProblems.connectorInvalid("metrics", e);
        }
        if (resolved.sources().isEmpty()) {
            throw TelemetryProblems.notCollected("metrics", engineId, group, sessionId);
        }
        return resolved;
    }
}
