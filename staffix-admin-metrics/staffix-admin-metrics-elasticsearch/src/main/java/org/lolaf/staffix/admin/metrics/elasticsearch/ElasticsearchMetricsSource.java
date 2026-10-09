package org.lolaf.staffix.admin.metrics.elasticsearch;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.elasticsearch.ElasticsearchClient;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.TIMESTAMP;
import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.scope;
import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.term;
import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.timeRange;

/**
 * Reads staffix's micrometer meters as the OpenTelemetry collector's {@code elasticsearch} exporter writes them with
 * its OTel mapping: a meter is the field {@code metrics.<meter>} of documents holding its tags as passthrough
 * attributes, a timer a delta {@code histogram} (or an {@code aggregate_metric_double} without a histogram), a gauge
 * a {@code double}. Points are computed per bucket of the query's step, each at the end of its bucket.
 */
public class ElasticsearchMetricsSource implements MetricsSource {

    private static final String SPLIT = "split";
    private static final String SERIES = "series";
    private static final String COUNT = "count";
    private static final String SUM = "sum";
    private static final String VALUE = "value";
    private static final String HELD = "held";
    private static final int MAX_SPLIT_VALUES = 1000;

    private final ElasticsearchClient client;
    private final double timerUnitsPerSecond;
    private final Duration minRateWindow;

    public ElasticsearchMetricsSource(ElasticsearchMetricsSettings settings, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.client = new ElasticsearchClient(settings, builder, jsonMapper, MetricsSourceException::new);
        this.timerUnitsPerSecond = settings.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
        this.minRateWindow = settings.getMinRateWindow();
    }

    @Override
    public void testConnection() {
        client.testConnection();
    }

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        String field = "metrics." + query.getMeter();
        Duration step = query.getStep();
        int stepsPerWindow = (int) Math.max(1, Math.ceilDiv(rateWindow(query).toMillis(), step.toMillis()));
        Duration window = step.multipliedBy(stepsPerWindow);
        double timerUnitsPerSecond = timerUnitsPerSecond(query);
        return search(query, field, switch (query.getStatistic()) {
            // the histograms are deltas: a window's rate is the recordings of its buckets over its length
            case RATE -> new Statistic(step, stepsPerWindow, window,
                    Map.of(COUNT, Map.of("value_count", Map.of("field", field))),
                    buckets -> buckets.stream().mapToLong(Bucket::documents).sum() == 0 ? null
                            : sum(buckets, COUNT) / (window.toMillis() / 1000.0));
            case MEAN -> new Statistic(step, stepsPerWindow, window,
                    Map.of(COUNT, Map.of("value_count", Map.of("field", field)), SUM, Map.of("sum", Map.of("field", field))),
                    buckets -> {
                        double count = sum(buckets, COUNT);
                        return count == 0 ? null : sum(buckets, SUM) / count / timerUnitsPerSecond;
                    });
            // percentiles of separate buckets do not add up: a quantile is read once per window
            case QUANTILE -> new Statistic(window, 1, window,
                    Map.of(VALUE, Map.of("percentiles", Map.of("field", field,
                            "percents", List.of(Objects.requireNonNullElse(query.getQuantile(), 0.5) * 100)))),
                    buckets -> {
                        JsonNode percentile = buckets.getFirst().aggregations().path(VALUE).path("values").valueStream().findFirst().orElse(null);
                        return percentile == null || !percentile.isNumber() ? null : percentile.asDouble() / timerUnitsPerSecond;
                    });
            case VALUE -> {
                double gaugeUnitsPerSecond = query.getGaugeUnit() == null ? 1 : query.getGaugeUnit().convert(1, TimeUnit.SECONDS);
                yield new Statistic(step, 1, null,
                        Map.of(VALUE, Map.of("avg", Map.of("field", field))),
                        buckets -> {
                            JsonNode average = buckets.getFirst().aggregations().path(VALUE).path(VALUE);
                            return average.isNumber() ? average.asDouble() / gaugeUnitsPerSecond : null;
                        });
            }
            case SHARE -> {
                Duration sampleInterval = query.getSampleInterval();
                double samplesPerStep = sampleInterval == null ? 0 : Math.max(1, (double) step.toMillis() / sampleInterval.toMillis());
                yield new Statistic(step, 1, null,
                        Map.of(HELD, Map.of("filter", Map.of("term", Map.of(field, Objects.requireNonNull(query.getValue(), "A share needs a value"))))),
                        buckets -> {
                            Bucket bucket = buckets.getFirst();
                            double samples = sampleInterval == null ? bucket.documents() : samplesPerStep;
                            return samples == 0 ? null : Math.min(1, bucket.aggregations().path(HELD).path("doc_count").asLong() / samples);
                        });
            }
        });
    }

    @Override
    public Duration getMinRateWindow() {
        return minRateWindow;
    }

    private Duration rateWindow(MetricQuery query) {
        Duration asked = Objects.requireNonNullElse(query.getRateWindow(), query.getStep());
        return asked.compareTo(minRateWindow) > 0 ? asked : minRateWindow;
    }

    private double timerUnitsPerSecond(MetricQuery query) {
        return query.getTimerBaseUnit() == null ? timerUnitsPerSecond : query.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
    }

    /**
     * One point per bucket of the statistic's interval ending within the query's range, from that bucket and the ones
     * before it in its window, fetched from before the range for the first points.
     */
    private List<MetricSeries> search(MetricQuery query, String field, Statistic statistic) {
        long intervalMillis = statistic.interval().toMillis();
        long start = Math.floorDiv(query.getFrom().minus(statistic.interval().multipliedBy(statistic.bucketsPerPoint())).toEpochMilli(), intervalMillis) * intervalMillis;
        Map<String, Object> histogram = new LinkedHashMap<>();
        histogram.put("field", TIMESTAMP);
        histogram.put("fixed_interval", intervalMillis + "ms");
        histogram.put("min_doc_count", 0);
        histogram.put("extended_bounds", Map.of("min", start, "max", query.getTo().toEpochMilli()));
        Map<String, Object> aggregations = Map.of(SERIES, Map.of("date_histogram", histogram, "aggs", statistic.aggregations()));
        for (String tag : query.getSplitBy().reversed()) {
            aggregations = Map.of(SPLIT, Map.of("terms", Map.of("field", tag, "size", MAX_SPLIT_VALUES), "aggs", aggregations));
        }
        List<Object> filters = scope(query.getEngineId(), query.getGroup(), query.getSessionId());
        query.getTagFilters().forEach((tag, value) -> filters.add(term(tag, value)));
        filters.add(Map.of("exists", Map.of("field", field)));
        filters.add(timeRange(start, query.getTo().toEpochMilli(), "epoch_millis"));
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("size", 0);
        search.put("track_total_hits", false);
        search.put("query", Map.of("bool", Map.of("filter", filters)));
        search.put("aggs", aggregations);
        Map<Map<String, String>, List<Bucket>> bucketsByLabels = new LinkedHashMap<>();
        collect(client.search(search).path("aggregations"), query.getSplitBy(), new LinkedHashMap<>(), bucketsByLabels);
        // the engines export every few seconds: the buckets after the newest export are not empty, just not known yet
        Instant newestData = bucketsByLabels.values().stream()
                .flatMap(List::stream)
                .filter(bucket -> bucket.documents() > 0)
                .map(Bucket::start)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MIN);
        return bucketsByLabels.entrySet().stream()
                .map(entry -> series(entry.getKey(), entry.getValue(), newestData, query, statistic))
                .filter(one -> !one.getPoints().isEmpty())
                .toList();
    }

    /**
     * The date histogram buckets of every combination of the split tags' values, by their values.
     */
    private static void collect(JsonNode aggregations, List<String> splitBy, Map<String, String> labels,
                                Map<Map<String, String>, List<Bucket>> bucketsByLabels) {
        if (labels.size() < splitBy.size()) {
            String tag = splitBy.get(labels.size());
            aggregations.path(SPLIT).path("buckets").forEach(split -> {
                Map<String, String> splitLabels = new LinkedHashMap<>(labels);
                splitLabels.put(tag, split.path("key").asString());
                collect(split, splitBy, splitLabels, bucketsByLabels);
            });
            return;
        }
        bucketsByLabels.put(labels, aggregations.path(SERIES).path("buckets").valueStream()
                .map(bucket -> new Bucket(Instant.ofEpochMilli(bucket.path("key").asLong()), bucket.path("doc_count").asLong(), bucket))
                .toList());
    }

    private static MetricSeries series(Map<String, String> labels, List<Bucket> buckets, Instant newestData, MetricQuery query, Statistic statistic) {
        MetricSeries.MetricSeriesBuilder builder = MetricSeries.builder().labels(labels).rateWindow(statistic.rateWindow());
        for (int last = statistic.bucketsPerPoint() - 1; last < buckets.size(); last++) {
            Instant end = buckets.get(last).start().plus(statistic.interval());
            if (end.isBefore(query.getFrom()) || end.isAfter(query.getTo()) || buckets.get(last).start().isAfter(newestData)) {
                continue;
            }
            Double value = statistic.reducer().reduce(buckets.subList(last - statistic.bucketsPerPoint() + 1, last + 1));
            if (value != null && Double.isFinite(value)) {
                builder.point(new MetricPoint(end, value));
            }
        }
        return builder.build();
    }

    private static double sum(List<Bucket> buckets, String aggregation) {
        return buckets.stream()
                .map(bucket -> bucket.aggregations().path(aggregation).path(VALUE))
                .mapToDouble(value -> value.isNumber() ? value.asDouble() : 0)
                .sum();
    }

    private record Bucket(Instant start, long documents, JsonNode aggregations) {
    }

    /**
     * How a statistic reads its buckets: each point reduces {@code bucketsPerPoint} buckets of {@code interval}, whose
     * aggregations are {@code aggregations}; {@code rateWindow} is what the series report, null for no rate.
     */
    private record Statistic(Duration interval, int bucketsPerPoint, @Nullable Duration rateWindow,
                             Map<String, Object> aggregations, Reducer reducer) {
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
