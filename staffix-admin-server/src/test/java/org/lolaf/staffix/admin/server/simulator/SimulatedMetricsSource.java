package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricStatistic;
import org.lolaf.staffix.admin.metrics.MetricsSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

import static org.lolaf.staffix.admin.server.simulator.SimulatedTelemetry.noise;

/**
 * Plausible shapes for any meter: a daily-ish traffic wave, latencies with occasional spikes, a drifting clock. One
 * series per combination of the split tags' values the filters keep.
 */
@RequiredArgsConstructor
class SimulatedMetricsSource implements MetricsSource {

    private static final String MESSAGE_TYPE = "fix.msg.type";
    private static final String DIRECTION = "fix.msg.dir";
    private static final String GROUP = "fix.sg";
    private static final String SESSION = "fix.sn";
    private static final Map<String, Double> MESSAGES_PER_SECOND = Map.of("D", 12.0, "8", 18.0, "0", 1 / 30.0);
    private static final Map<String, Double> MEDIAN_MICROS = Map.of(
            "messages.read.latency", 14.0,
            "messages.write.latency", 8.0,
            "messages.decoding.latency", 3.0,
            "messages.encoding.latency", 2.0,
            "session.rtt", 450.0);
    private static final List<String> VENUES = List.of("xpar", "xlon");
    private static final double MICROS = 1e-6;

    private final SimulatedEngineGateway gateway;

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        List<SimulatedSession> sessions = SimulatedTelemetry.runningConfigs(gateway, query.getEngineId(), query.getGroup(), query.getSessionId());
        boolean perSession = query.getSplitBy().contains(GROUP) || query.getSplitBy().contains(SESSION);
        List<Map<String, String>> combinations = new ArrayList<>(List.of(Map.of()));
        for (String tag : query.getSplitBy()) {
            if (tag.equals(GROUP) || tag.equals(SESSION)) {
                continue;
            }
            List<Map<String, String>> next = new ArrayList<>();
            combinations.forEach(combination -> valuesOf(tag, query.getTagFilters()).forEach(value -> {
                Map<String, String> extended = new LinkedHashMap<>(combination);
                extended.put(tag, value);
                next.add(extended);
            }));
            combinations = next;
        }
        List<MetricSeries> series = new ArrayList<>();
        for (Map<String, String> combination : combinations) {
            String messageType = combination.get(MESSAGE_TYPE);
            String suffix = query.getMeter() + combination;
            if (perSession) {
                sessions.forEach(session -> {
                    Map<String, String> labels = new LinkedHashMap<>(combination);
                    if (query.getSplitBy().contains(GROUP)) {
                        labels.put(GROUP, session.group);
                    }
                    if (query.getSplitBy().contains(SESSION)) {
                        labels.put(SESSION, session.id);
                    }
                    String key = SimulatedTelemetry.key(session) + suffix;
                    series.add(series(query, labels, t -> value(query, key, messageType, t)));
                });
            } else if (!sessions.isEmpty()) {
                List<String> keys = sessions.stream().map(session -> SimulatedTelemetry.key(session) + suffix).toList();
                series.add(series(query, combination, t -> keys.stream()
                        .mapToDouble(key -> value(query, key, messageType, t))
                        .reduce(0, query.getStatistic() == MetricStatistic.RATE ? Double::sum : Math::max)));
            }
        }
        return series;
    }

    private static List<String> valuesOf(String tag, Map<String, String> filters) {
        if (filters.containsKey(tag)) {
            return List.of(filters.get(tag));
        }
        return switch (tag) {
            case MESSAGE_TYPE -> MESSAGES_PER_SECOND.keySet().stream().sorted().toList();
            case DIRECTION -> List.of("in", "out");
            case SimulatedMeters.CUSTOM_TAG -> VENUES;
            default -> List.of("default");
        };
    }

    private static MetricSeries series(MetricQuery query, Map<String, String> labels, ToDoubleFunction<Instant> value) {
        MetricSeries.MetricSeriesBuilder series = MetricSeries.builder()
                .labels(labels)
                .rateWindow(query.getStatistic() == MetricStatistic.VALUE ? null
                        : Objects.requireNonNullElse(query.getRateWindow(), query.getStep()));
        long step = query.getStep().toSeconds();
        long start = query.getFrom().getEpochSecond() / step * step;
        for (long second = start; second <= query.getTo().getEpochSecond(); second += step) {
            Instant t = Instant.ofEpochSecond(second);
            series.point(new MetricPoint(t, value.applyAsDouble(t)));
        }
        return series.build();
    }

    private static double value(MetricQuery query, String key, @Nullable String messageType, Instant t) {
        long second = t.getEpochSecond();
        double random = noise(key, second / query.getStep().toSeconds());
        double wave = 1 + 0.5 * Math.sin(2 * Math.PI * second / 7200.0 + key.hashCode());
        double median = MEDIAN_MICROS.getOrDefault(query.getMeter(), 120.0);
        return switch (query.getStatistic()) {
            case RATE -> messageType != null ? MESSAGES_PER_SECOND.get(messageType) * wave * (0.8 + 0.4 * random) : 5 * wave * (0.8 + 0.4 * random);
            case QUANTILE -> latency(median, query.getQuantile(), random);
            case MEAN -> query.getMeter().equals("session.rtt") ? (350 + 150 * wave + 60 * random) * MICROS : 1.2 * latency(median, 0.5, random);
            case VALUE -> switch (query.getMeter()) {
                case "session.clock.offset" -> (40 * Math.sin(2 * Math.PI * second / 3600.0 + key.hashCode()) + 8 * (random - 0.5)) * MICROS;
                case "session.logon.state" -> 1;
                default -> 0;
            };
        };
    }

    private static double latency(double medianMicros, @Nullable Double quantile, double random) {
        double q = quantile == null ? 0.5 : quantile;
        double tail = 1 + 12 * Math.pow(Math.max(q - 0.5, 0) * 2, 3);
        double spike = random > 0.99 ? 3 : 1;
        return medianMicros * tail * spike * (0.85 + 0.3 * random) * MICROS;
    }
}
