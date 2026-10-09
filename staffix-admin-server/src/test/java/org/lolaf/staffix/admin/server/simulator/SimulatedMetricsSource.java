package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
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

/**
 * One series per combination of the split tags' values the filters keep, a point per step at a constant value: the
 * tests check what a query gives, not what a meter measures.
 */
@RequiredArgsConstructor
class SimulatedMetricsSource implements MetricsSource {

    private static final String MESSAGE_TYPE = "fix.msg.type";
    private static final String DIRECTION = "fix.msg.dir";
    private static final String GROUP = "fix.sg";
    private static final String SESSION = "fix.sn";
    private static final List<String> MESSAGE_TYPES = List.of("0", "8", "D");
    private static final List<String> VENUES = List.of("xpar", "xlon");

    private final SimulatedEngineGateway gateway;

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        List<SimulatedSession> sessions = gateway.runningConfigs(query.getEngineId(), query.getGroup(), query.getSessionId());
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> combinations = sessionLabels(query.getSplitBy(), sessions);
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
        return combinations.stream().map(labels -> series(query, labels)).toList();
    }

    /**
     * Split by group or session, a series per running session; a single combination otherwise.
     */
    private static List<Map<String, String>> sessionLabels(List<String> splitBy, List<SimulatedSession> sessions) {
        if (!splitBy.contains(GROUP) && !splitBy.contains(SESSION)) {
            return List.of(Map.of());
        }
        return sessions.stream()
                .map(session -> {
                    Map<String, String> labels = new LinkedHashMap<>();
                    if (splitBy.contains(GROUP)) {
                        labels.put(GROUP, session.group);
                    }
                    if (splitBy.contains(SESSION)) {
                        labels.put(SESSION, session.id);
                    }
                    return labels;
                })
                .distinct()
                .toList();
    }

    private static List<String> valuesOf(String tag, Map<String, String> filters) {
        if (filters.containsKey(tag)) {
            return List.of(filters.get(tag));
        }
        return switch (tag) {
            case MESSAGE_TYPE -> MESSAGE_TYPES;
            case DIRECTION -> List.of("in", "out");
            case SimulatedMeters.CUSTOM_TAG -> VENUES;
            default -> List.of("default");
        };
    }

    private static MetricSeries series(MetricQuery query, Map<String, String> labels) {
        MetricSeries.MetricSeriesBuilder series = MetricSeries.builder()
                .labels(labels)
                .rateWindow(query.getStatistic() == MetricStatistic.VALUE || query.getStatistic() == MetricStatistic.SHARE ? null
                        : Objects.requireNonNullElse(query.getRateWindow(), query.getStep()));
        double value = query.getStatistic() == MetricStatistic.SHARE ? loggedInShare(query) : 1;
        long step = query.getStep().toSeconds();
        long start = query.getFrom().getEpochSecond() / step * step;
        for (long second = start; second <= query.getTo().getEpochSecond(); second += step) {
            series.point(new MetricPoint(Instant.ofEpochSecond(second), value));
        }
        return series.build();
    }

    /**
     * Always logged in.
     */
    private static double loggedInShare(MetricQuery query) {
        return Objects.equals(query.getValue(), 1.0) ? 1 : 0;
    }
}
