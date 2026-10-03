package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.lolaf.staffix.admin.server.simulator.SimulatedTelemetry.noise;

/**
 * Plausible shapes for each kind: a daily-ish traffic wave, latencies with occasional spikes, a drifting clock.
 */
@RequiredArgsConstructor
class SimulatedMetricsSource implements MetricsSource {

    private static final Map<String, Double> MESSAGES_PER_SECOND = Map.of("D", 12.0, "8", 18.0, "0", 1 / 30.0);
    private static final List<String> DIRECTIONS = List.of("in", "out");
    private static final double MICROS = 1e-6;

    private final SimulatedEngineGateway gateway;

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        List<SimulatedSession> sessions = SimulatedTelemetry.runningConfigs(gateway, query.getEngineId(), query.getGroup(), query.getSessionId());
        List<MetricSeries> series = new ArrayList<>();
        if (query.getKind().isPerMessageType()) {
            MESSAGES_PER_SECOND.keySet().stream().sorted().forEach(type -> DIRECTIONS.forEach(direction ->
                    series.add(series(query, Map.of(MetricSeries.MESSAGE_TYPE, type, MetricSeries.DIRECTION, direction),
                            t -> sessions.stream()
                                    .mapToDouble(s -> value(query, SimulatedTelemetry.key(s) + type + direction, type, t))
                                    .reduce(0, query.getKind() == MetricKind.MESSAGE_RATE ? Double::sum : Math::max)))));
        } else {
            sessions.forEach(s -> series.add(series(query, Map.of(MetricSeries.GROUP, s.group, MetricSeries.SESSION_ID, s.id),
                    t -> value(query, SimulatedTelemetry.key(s), null, t))));
        }
        return series;
    }

    private static MetricSeries series(MetricQuery query, Map<String, String> labels, ToDoubleFunction<Instant> value) {
        MetricSeries.MetricSeriesBuilder series = MetricSeries.builder().labels(labels);
        long step = query.getStep().toSeconds();
        long start = query.getFrom().getEpochSecond() / step * step;
        for (long second = start; second <= query.getTo().getEpochSecond(); second += step) {
            Instant t = Instant.ofEpochSecond(second);
            series.point(new MetricPoint(t, value.applyAsDouble(t)));
        }
        return series.build();
    }

    private static double value(MetricQuery query, String key, String messageType, Instant t) {
        long second = t.getEpochSecond();
        double random = noise(key, second / query.getStep().toSeconds());
        double wave = 1 + 0.5 * Math.sin(2 * Math.PI * second / 7200.0 + key.hashCode());
        return switch (query.getKind()) {
            case MESSAGE_RATE -> MESSAGES_PER_SECOND.get(messageType) * wave * (0.8 + 0.4 * random);
            case READ_LATENCY -> latency(14, query.getQuantile(), random);
            case WRITE_LATENCY -> latency(8, query.getQuantile(), random);
            case DECODING_LATENCY -> latency(3, query.getQuantile(), random);
            case ENCODING_LATENCY -> latency(2, query.getQuantile(), random);
            case ROUND_TRIP_TIME -> (350 + 150 * wave + 60 * random) * MICROS;
            case CLOCK_OFFSET -> (40 * Math.sin(2 * Math.PI * second / 3600.0 + key.hashCode()) + 8 * (random - 0.5)) * MICROS;
        };
    }

    private static double latency(double medianMicros, Double quantile, double random) {
        double q = quantile == null ? 0.5 : quantile;
        double tail = 1 + 12 * Math.pow(Math.max(q - 0.5, 0) * 2, 3);
        double spike = random > 0.99 ? 3 : 1;
        return medianMicros * tail * spike * (0.85 + 0.3 * random) * MICROS;
    }
}
