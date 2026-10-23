/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.metrics.clickhouse;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.clickhouse.ClickHouseClient;
import org.lolaf.staffix.admin.clickhouse.ClickHouseParameters;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.lolaf.staffix.admin.clickhouse.ClickHouseQueries.attribute;
import static org.lolaf.staffix.admin.clickhouse.ClickHouseQueries.attributeEquals;
import static org.lolaf.staffix.admin.clickhouse.ClickHouseQueries.scope;

/**
 * Reads staffix's micrometer meters as the OpenTelemetry collector's {@code clickhouse} exporter stores them: a timer
 * as a cumulative histogram row per export, a gauge as a value row per export, tags in each data point's attributes.
 * A timer's rows become per-series differences between exports, so a restarted engine counts from its start. Points
 * are computed per bucket of the query's step, each at the end of its bucket.
 */
public class ClickHouseMetricsSource implements MetricsSource {

    private static final String ATTRIBUTES = "Attributes";
    /**
     * How far before the first bucket a timer's rows are read, so its first export in range has one to differ from.
     */
    private static final Duration PREVIOUS_EXPORT_LOOKBACK = Duration.ofMinutes(5);
    private static final String BUCKET = "bucket";
    private static final String SAMPLES = "samples";

    private final ClickHouseClient client;
    private final String histogramTable;
    private final String gaugeTable;
    private final String sumTable;
    private final double timerUnitsPerSecond;
    private final Duration minRateWindow;

    public ClickHouseMetricsSource(ClickHouseMetricsSettings settings, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.client = new ClickHouseClient(settings, builder, jsonMapper, MetricsSourceException::new);
        this.histogramTable = settings.getHistogramTable();
        this.gaugeTable = settings.getGaugeTable();
        this.sumTable = settings.getSumTable();
        this.timerUnitsPerSecond = settings.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
        this.minRateWindow = settings.getMinRateWindow();
    }

    @Override
    public void testConnection() {
        client.testConnection(Set.of(histogramTable, gaugeTable, sumTable));
    }

    @Override
    public Duration getMinRateWindow() {
        return minRateWindow;
    }

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        Duration step = query.getStep();
        int stepsPerWindow = (int) Math.max(1, Math.ceilDiv(rateWindow(query).toMillis(), step.toMillis()));
        Duration window = step.multipliedBy(stepsPerWindow);
        double timerUnitsPerSecond = timerUnitsPerSecond(query);
        return switch (query.getStatistic()) {
            case RATE -> timers(query, new Statistic(step, stepsPerWindow, window,
                    buckets -> sum(buckets, SAMPLES) == 0 ? null : sum(buckets, "count") / (window.toMillis() / 1000.0)));
            case COUNTER_RATE -> counters(query, new Statistic(step, stepsPerWindow, window,
                    buckets -> sum(buckets, SAMPLES) == 0 ? null : sum(buckets, "increase") / (window.toMillis() / 1000.0)));
            case MEAN -> timers(query, new Statistic(step, stepsPerWindow, window,
                    buckets -> {
                        double count = sum(buckets, "count");
                        return count == 0 ? null : sum(buckets, "sum") / count / timerUnitsPerSecond;
                    }));
            // histogram buckets of separate steps would add up, but a window's are read at once like the other backends'
            case QUANTILE -> timers(query, new Statistic(window, 1, window,
                    buckets -> {
                        JsonNode row = buckets.getFirst().row();
                        Double quantile = row == null ? null : quantile(Objects.requireNonNullElse(query.getQuantile(), 0.5), row);
                        return quantile == null ? null : quantile / timerUnitsPerSecond;
                    }));
            case VALUE -> {
                double gaugeUnitsPerSecond = query.getGaugeUnit() == null ? 1 : query.getGaugeUnit().convert(1, TimeUnit.SECONDS);
                yield gauges(query, "avg(Value) AS value", new ClickHouseParameters(), new Statistic(step, 1, null,
                        buckets -> {
                            JsonNode row = buckets.getFirst().row();
                            return row == null ? null : row.path("value").asDouble() / gaugeUnitsPerSecond;
                        }));
            }
            case SHARE -> {
                ClickHouseParameters parameters = new ClickHouseParameters();
                String held = "countIf(Value = " + parameters.add("Float64", Objects.requireNonNull(query.getValue(), "A share needs a value")) + ") AS held";
                Duration sampleInterval = query.getSampleInterval();
                double samplesPerStep = sampleInterval == null ? 0 : Math.max(1, (double) step.toMillis() / sampleInterval.toMillis());
                yield gauges(query, held, parameters, new Statistic(step, 1, null,
                        buckets -> {
                            JsonNode row = buckets.getFirst().row();
                            double samples = sampleInterval == null ? buckets.getFirst().samples() : samplesPerStep;
                            double heldSamples = row == null ? 0 : row.path("held").asDouble();
                            return samples == 0 ? null : Math.min(1, heldSamples / samples);
                        }));
            }
        };
    }

    private Duration rateWindow(MetricQuery query) {
        Duration asked = Objects.requireNonNullElse(query.getRateWindow(), query.getStep());
        return asked.compareTo(minRateWindow) > 0 ? asked : minRateWindow;
    }

    private double timerUnitsPerSecond(MetricQuery query) {
        return query.getTimerBaseUnit() == null ? timerUnitsPerSecond : query.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
    }

    /**
     * Each export's increase over the previous export of the same series, summed per bucket; a series' first row
     * counts only when the series started after the rows read began. {@link #counters} reads a counter's total alike.
     */
    private List<MetricSeries> timers(MetricQuery query, Statistic statistic) {
        ClickHouseParameters parameters = new ClickHouseParameters();
        long start = start(query, statistic);
        String interval = parameters.add("Int64", statistic.interval().toMillis());
        String readFrom = "toDateTime(" + parameters.add("Int64", Instant.ofEpochMilli(start).minus(PREVIOUS_EXPORT_LOOKBACK).getEpochSecond()) + ")";
        List<String> labels = labels(query);
        String labelColumns = labels.stream().map(label -> label + ", ").collect(Collectors.joining());
        String sql = "SELECT " + labelColumns + BUCKET + ", sum(dCount) AS count, sum(dSum) AS sum, sumForEach(dBuckets) AS buckets, "
                + "any(bounds) AS bounds, count() AS " + SAMPLES
                + " FROM (SELECT " + labelColumns + BUCKET + ", bounds, StartTimeUnix, n, toInt64(Count) - toInt64(prevCount) AS dCount, "
                + "Sum - prevSum AS dSum, arrayMap((c, i) -> toInt64(c) - toInt64(prevBuckets[i]), BucketCounts, arrayEnumerate(BucketCounts)) AS dBuckets"
                + " FROM (SELECT " + labelExpressions(query, labels, parameters)
                + "intDiv(toUnixTimestamp(TimeUnix) * 1000, " + interval + ") * " + interval + " AS " + BUCKET + ", "
                + "ExplicitBounds AS bounds, Count, Sum, BucketCounts, StartTimeUnix, "
                + "lagInFrame(Count) OVER w AS prevCount, lagInFrame(Sum) OVER w AS prevSum, lagInFrame(BucketCounts) OVER w AS prevBuckets, "
                + "row_number() OVER w AS n"
                + " FROM " + parameters.add("Identifier", histogramTable)
                + " WHERE " + String.join(" AND ", conditions(query, readFrom, parameters))
                + " WINDOW w AS (PARTITION BY ResourceAttributes, Attributes, StartTimeUnix ORDER BY TimeUnix ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)))"
                + " WHERE (n > 1 OR StartTimeUnix >= " + readFrom + ") AND " + BUCKET + " >= " + parameters.add("Int64", start)
                + " GROUP BY " + labelColumns + BUCKET;
        return series(query, statistic, start, labels, client.query(sql, parameters));
    }

    private List<MetricSeries> counters(MetricQuery query, Statistic statistic) {
        ClickHouseParameters parameters = new ClickHouseParameters();
        long start = start(query, statistic);
        String interval = parameters.add("Int64", statistic.interval().toMillis());
        String readFrom = "toDateTime(" + parameters.add("Int64", Instant.ofEpochMilli(start).minus(PREVIOUS_EXPORT_LOOKBACK).getEpochSecond()) + ")";
        List<String> labels = labels(query);
        String labelColumns = labels.stream().map(label -> label + ", ").collect(Collectors.joining());
        String sql = "SELECT " + labelColumns + BUCKET + ", sum(dValue) AS increase, count() AS " + SAMPLES
                + " FROM (SELECT " + labelColumns + BUCKET + ", StartTimeUnix, n, Value - prevValue AS dValue"
                + " FROM (SELECT " + labelExpressions(query, labels, parameters)
                + "intDiv(toUnixTimestamp(TimeUnix) * 1000, " + interval + ") * " + interval + " AS " + BUCKET + ", "
                + "Value, StartTimeUnix, lagInFrame(Value) OVER w AS prevValue, row_number() OVER w AS n"
                + " FROM " + parameters.add("Identifier", sumTable)
                + " WHERE " + String.join(" AND ", conditions(query, readFrom, parameters))
                + " WINDOW w AS (PARTITION BY ResourceAttributes, Attributes, StartTimeUnix ORDER BY TimeUnix ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)))"
                + " WHERE (n > 1 OR StartTimeUnix >= " + readFrom + ") AND " + BUCKET + " >= " + parameters.add("Int64", start)
                + " GROUP BY " + labelColumns + BUCKET;
        return series(query, statistic, start, labels, client.query(sql, parameters));
    }

    private List<MetricSeries> gauges(MetricQuery query, String aggregate, ClickHouseParameters parameters, Statistic statistic) {
        long start = start(query, statistic);
        String interval = parameters.add("Int64", statistic.interval().toMillis());
        String readFrom = "toDateTime(" + parameters.add("Int64", Instant.ofEpochMilli(start).getEpochSecond()) + ")";
        List<String> labels = labels(query);
        String sql = "SELECT " + labelExpressions(query, labels, parameters)
                + "intDiv(toUnixTimestamp(TimeUnix) * 1000, " + interval + ") * " + interval + " AS " + BUCKET + ", "
                + aggregate + ", count() AS " + SAMPLES
                + " FROM " + parameters.add("Identifier", gaugeTable)
                + " WHERE " + String.join(" AND ", conditions(query, readFrom, parameters))
                + " GROUP BY " + labels.stream().map(label -> label + ", ").collect(Collectors.joining()) + BUCKET;
        return series(query, statistic, start, labels, client.query(sql, parameters));
    }

    private static long start(MetricQuery query, Statistic statistic) {
        long intervalMillis = statistic.interval().toMillis();
        return Math.floorDiv(query.getFrom().minus(statistic.interval().multipliedBy(statistic.bucketsPerPoint())).toEpochMilli(), intervalMillis) * intervalMillis;
    }

    private static List<String> labels(MetricQuery query) {
        return IntStream.range(0, query.getSplitBy().size()).mapToObj(i -> "l" + i).toList();
    }

    private static String labelExpressions(MetricQuery query, List<String> labels, ClickHouseParameters parameters) {
        StringBuilder expressions = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            expressions.append(attribute(ATTRIBUTES, query.getSplitBy().get(i), parameters)).append(" AS ").append(labels.get(i)).append(", ");
        }
        return expressions.toString();
    }

    private static List<String> conditions(MetricQuery query, String readFrom, ClickHouseParameters parameters) {
        List<String> conditions = new ArrayList<>();
        conditions.add("MetricName = " + parameters.string(query.getMeter()));
        conditions.add("TimeUnix >= " + readFrom);
        conditions.add("TimeUnix <= toDateTime(" + parameters.add("Int64", query.getTo().getEpochSecond()) + ")");
        conditions.addAll(scope(ATTRIBUTES, query.getEngineId(), query.getGroup(), query.getSessionId(), parameters));
        query.getTagFilters().forEach((tag, value) -> conditions.add(attributeEquals(ATTRIBUTES, tag, value, parameters)));
        return conditions;
    }

    /**
     * One point per bucket of the statistic's interval ending within the query's range, from that bucket and the ones
     * before it in its window; buckets without a row hold nothing, those after the newest export are not known yet.
     */
    private static List<MetricSeries> series(MetricQuery query, Statistic statistic, long start, List<String> labels, List<JsonNode> rows) {
        Map<Map<String, String>, Map<Long, JsonNode>> rowsByLabels = new LinkedHashMap<>();
        long newestData = Long.MIN_VALUE;
        for (JsonNode row : rows) {
            Map<String, String> seriesLabels = new LinkedHashMap<>();
            for (int i = 0; i < labels.size(); i++) {
                seriesLabels.put(query.getSplitBy().get(i), row.path(labels.get(i)).asString());
            }
            long bucket = row.path(BUCKET).asLong();
            rowsByLabels.computeIfAbsent(seriesLabels, ignored -> new HashMap<>()).put(bucket, row);
            newestData = Math.max(newestData, bucket);
        }
        long intervalMillis = statistic.interval().toMillis();
        List<MetricSeries> series = new ArrayList<>();
        for (Map.Entry<Map<String, String>, Map<Long, JsonNode>> entry : rowsByLabels.entrySet()) {
            List<Bucket> buckets = new ArrayList<>();
            for (long bucket = start; bucket <= query.getTo().toEpochMilli(); bucket += intervalMillis) {
                buckets.add(new Bucket(bucket, entry.getValue().get(bucket)));
            }
            MetricSeries.MetricSeriesBuilder builder = MetricSeries.builder().labels(entry.getKey()).rateWindow(statistic.rateWindow());
            for (int last = statistic.bucketsPerPoint() - 1; last < buckets.size(); last++) {
                Instant end = Instant.ofEpochMilli(buckets.get(last).start() + intervalMillis);
                if (end.isBefore(query.getFrom()) || end.isAfter(query.getTo()) || buckets.get(last).start() > newestData) {
                    continue;
                }
                Double value = statistic.reducer().reduce(buckets.subList(last - statistic.bucketsPerPoint() + 1, last + 1));
                if (value != null && Double.isFinite(value)) {
                    builder.point(new MetricPoint(end, value));
                }
            }
            MetricSeries built = builder.build();
            if (!built.getPoints().isEmpty()) {
                series.add(built);
            }
        }
        return series;
    }

    private static double sum(List<Bucket> buckets, String column) {
        return buckets.stream().map(Bucket::row).filter(Objects::nonNull).mapToDouble(row -> row.path(column).asDouble()).sum();
    }

    /**
     * Interpolated linearly within the bucket holding the rank, from 0 for the first; the overflow bucket answers the
     * highest bound, as Prometheus does.
     */
    static @Nullable Double quantile(double quantile, JsonNode row) {
        List<Double> bounds = row.path("bounds").valueStream().map(JsonNode::asDouble).toList();
        List<Long> counts = row.path("buckets").valueStream().map(JsonNode::asLong).toList();
        long total = counts.stream().mapToLong(Long::longValue).sum();
        if (total <= 0 || bounds.isEmpty()) {
            return null;
        }
        double rank = quantile * total;
        long below = 0;
        for (int i = 0; i < counts.size(); i++) {
            long count = counts.get(i);
            if (count > 0 && below + count >= rank) {
                if (i >= bounds.size()) {
                    return bounds.getLast();
                }
                double lower = i == 0 ? 0 : bounds.get(i - 1);
                return lower + (bounds.get(i) - lower) * (rank - below) / count;
            }
            below += count;
        }
        return bounds.getLast();
    }

    private record Bucket(long start, @Nullable JsonNode row) {

        long samples() {
            return row == null ? 0 : row.path(SAMPLES).asLong();
        }
    }

    /**
     * How a statistic reads its buckets: each point reduces {@code bucketsPerPoint} buckets of {@code interval};
     * {@code rateWindow} is what the series report, null for no rate.
     */
    private record Statistic(Duration interval, int bucketsPerPoint, @Nullable Duration rateWindow, Reducer reducer) {
    }

    @FunctionalInterface
    private interface Reducer {

        /**
         * A point's value from its buckets, oldest first; null for no point.
         */
        @Nullable
        Double reduce(List<Bucket> buckets);
    }
}
