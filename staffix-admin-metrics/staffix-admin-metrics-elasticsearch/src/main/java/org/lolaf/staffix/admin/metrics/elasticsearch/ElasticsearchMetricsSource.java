package org.lolaf.staffix.admin.metrics.elasticsearch;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Reads staffix's micrometer meters as the OpenTelemetry collector's {@code elasticsearch} exporter writes them with
 * its OTel mapping: a meter is the field {@code metrics.<meter>} of documents holding its tags as passthrough
 * attributes, a timer a delta {@code histogram} (or an {@code aggregate_metric_double} without a histogram), a gauge
 * a {@code double}. Points are computed per bucket of the query's step, each at the end of its bucket.
 */
public class ElasticsearchMetricsSource implements MetricsSource {

    private static final String TIMESTAMP = "@timestamp";
    private static final String ENGINE = FixMonitoringAttributes.FIX_ENGINE_ID.getKey();
    private static final String GROUP = FixMonitoringAttributes.FIX_SESSION_GROUP.getKey();
    private static final String SESSION = FixMonitoringAttributes.FIX_SESSION_NAME.getKey();
    private static final String SPLIT = "split";
    private static final String SERIES = "series";
    private static final String COUNT = "count";
    private static final String SUM = "sum";
    private static final String VALUE = "value";
    private static final int MAX_SPLIT_VALUES = 1000;

    private final RestClient client;
    private final URI rootUrl;
    private final URI searchUrl;
    private final URI resolveUrl;
    private final String index;
    private final double timerUnitsPerSecond;
    private final Duration minRateWindow;
    private final JsonMapper jsonMapper;

    public ElasticsearchMetricsSource(ElasticsearchMetricsSettings properties, RestClient.Builder builder, JsonMapper jsonMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build());
        requestFactory.setReadTimeout(properties.getTimeout());
        if (properties.getApiKey() != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + properties.getApiKey());
        } else if (properties.getUsername() != null) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(properties.getUsername(), Objects.requireNonNullElse(properties.getPassword(), "")));
        }
        this.client = builder.requestFactory(requestFactory).build();
        String url = properties.getUrl().toString().replaceAll("/+$", "");
        this.index = properties.getIndex();
        this.rootUrl = URI.create(url + "/");
        this.searchUrl = UriComponentsBuilder.fromUriString(url).pathSegment(index, "_search").encode().build().toUri();
        this.resolveUrl = UriComponentsBuilder.fromUriString(url).pathSegment("_resolve", "index", index).encode().build().toUri();
        this.timerUnitsPerSecond = properties.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
        this.minRateWindow = properties.getMinRateWindow();
        this.jsonMapper = jsonMapper;
    }

    /**
     * Reads the cluster's info, which also checks the credentials, then makes sure the index matches something.
     */
    @Override
    public void testConnection() {
        get(rootUrl);
        JsonNode resolved = get(resolveUrl);
        if (resolved.path("indices").isEmpty() && resolved.path("aliases").isEmpty() && resolved.path("data_streams").isEmpty()) {
            throw new MetricsSourceException("Elasticsearch has no index or data stream matching " + index, null);
        }
    }

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        String field = "metrics." + query.getMeter();
        Duration step = query.getStep();
        long stepsPerWindow = Math.max(1, Math.ceilDiv(rateWindow(query).toMillis(), step.toMillis()));
        Duration window = step.multipliedBy(stepsPerWindow);
        double timerUnitsPerSecond = timerUnitsPerSecond(query);
        return switch (query.getStatistic()) {
            // the histograms are deltas: a window's rate is the recordings of its buckets over its length
            case RATE -> search(query, field, step, (int) stepsPerWindow, window,
                    Map.of(COUNT, Map.of("value_count", Map.of("field", field))),
                    buckets -> {
                        long documents = buckets.stream().mapToLong(Bucket::documents).sum();
                        return documents == 0 ? null : buckets.stream().mapToDouble(bucket -> value(bucket, COUNT)).sum() / seconds(window);
                    });
            case MEAN -> search(query, field, step, (int) stepsPerWindow, window,
                    Map.of(COUNT, Map.of("value_count", Map.of("field", field)), SUM, Map.of("sum", Map.of("field", field))),
                    buckets -> {
                        double count = buckets.stream().mapToDouble(bucket -> value(bucket, COUNT)).sum();
                        return count == 0 ? null : buckets.stream().mapToDouble(bucket -> value(bucket, SUM)).sum() / count / timerUnitsPerSecond;
                    });
            // percentiles of separate buckets do not add up: a quantile is read once per window
            case QUANTILE -> search(query, field, window, 1, window,
                    Map.of(VALUE, Map.of("percentiles", Map.of("field", field,
                            "percents", List.of(Objects.requireNonNullElse(query.getQuantile(), 0.5) * 100)))),
                    buckets -> {
                        JsonNode percentile = buckets.getFirst().aggregations().path(VALUE).path("values").valueStream().findFirst().orElse(null);
                        return percentile == null || !percentile.isNumber() ? null : percentile.asDouble() / timerUnitsPerSecond;
                    });
            case VALUE -> {
                double gaugeUnitsPerSecond = query.getGaugeUnit() == null ? 1 : query.getGaugeUnit().convert(1, TimeUnit.SECONDS);
                yield search(query, field, step, 1, null,
                        Map.of(VALUE, Map.of("avg", Map.of("field", field))),
                        buckets -> {
                            JsonNode average = buckets.getFirst().aggregations().path(VALUE).path(VALUE);
                            return average.isNumber() ? average.asDouble() / gaugeUnitsPerSecond : null;
                        });
            }
        };
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
     * One point per bucket of {@code interval} ending within the query's range, from that bucket and the
     * {@code bucketsPerPoint - 1} before it, fetched from before the range for the first points.
     */
    private List<MetricSeries> search(MetricQuery query, String field, Duration interval, int bucketsPerPoint,
                                      @Nullable Duration rateWindow, Map<String, Object> statistic, Reducer reducer) {
        long intervalMillis = interval.toMillis();
        Instant start = Instant.ofEpochMilli(Math.floorDiv(query.getFrom().minus(interval.multipliedBy(bucketsPerPoint)).toEpochMilli(), intervalMillis) * intervalMillis);
        Map<String, Object> histogram = new LinkedHashMap<>();
        histogram.put("field", TIMESTAMP);
        histogram.put("fixed_interval", intervalMillis + "ms");
        histogram.put("min_doc_count", 0);
        histogram.put("extended_bounds", Map.of("min", start.toEpochMilli(), "max", query.getTo().toEpochMilli()));
        Map<String, Object> aggregations = Map.of(SERIES, Map.of("date_histogram", histogram, "aggs", statistic));
        for (String tag : query.getSplitBy().reversed()) {
            aggregations = Map.of(SPLIT, Map.of("terms", Map.of("field", tag, "size", MAX_SPLIT_VALUES), "aggs", aggregations));
        }
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("size", 0);
        search.put("track_total_hits", false);
        search.put("query", Map.of("bool", Map.of("filter", filters(query, field, start))));
        search.put("aggs", aggregations);
        JsonNode response = post(search);
        Map<Map<String, String>, List<Bucket>> bucketsByLabels = new LinkedHashMap<>();
        collect(response.path("aggregations"), query.getSplitBy(), new LinkedHashMap<>(), bucketsByLabels);
        // the engines export every few seconds: the buckets after the newest export are not empty, just not known yet
        Instant newestData = bucketsByLabels.values().stream()
                .flatMap(List::stream)
                .filter(bucket -> bucket.documents() > 0)
                .map(Bucket::start)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MIN);
        return bucketsByLabels.entrySet().stream()
                .map(entry -> series(entry.getKey(), entry.getValue(), newestData, query, interval, bucketsPerPoint, rateWindow, reducer))
                .filter(one -> !one.getPoints().isEmpty())
                .toList();
    }

    private static List<Object> filters(MetricQuery query, String field, Instant start) {
        List<Object> filters = new ArrayList<>();
        filters.add(term(ENGINE, query.getEngineId()));
        if (query.getGroup() != null) {
            filters.add(term(GROUP, query.getGroup()));
        }
        if (query.getSessionId() != null) {
            filters.add(term(SESSION, query.getSessionId()));
        }
        query.getTagFilters().forEach((tag, value) -> filters.add(term(tag, value)));
        filters.add(Map.of("exists", Map.of("field", field)));
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("gte", start.toEpochMilli());
        range.put("lt", query.getTo().toEpochMilli());
        range.put("format", "epoch_millis");
        filters.add(Map.of("range", Map.of(TIMESTAMP, range)));
        return filters;
    }

    private static Map<String, Object> term(String field, String value) {
        return Map.of("term", Map.of(field, value));
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

    private static MetricSeries series(Map<String, String> labels, List<Bucket> buckets, Instant newestData, MetricQuery query,
                                       Duration interval, int bucketsPerPoint, @Nullable Duration rateWindow, Reducer reducer) {
        MetricSeries.MetricSeriesBuilder builder = MetricSeries.builder().labels(labels).rateWindow(rateWindow);
        for (int last = bucketsPerPoint - 1; last < buckets.size(); last++) {
            Instant end = buckets.get(last).start().plus(interval);
            if (end.isBefore(query.getFrom()) || end.isAfter(query.getTo()) || buckets.get(last).start().isAfter(newestData)) {
                continue;
            }
            Double value = reducer.reduce(buckets.subList(last - bucketsPerPoint + 1, last + 1));
            if (value != null && Double.isFinite(value)) {
                builder.point(new MetricPoint(end, value));
            }
        }
        return builder.build();
    }

    private static double value(Bucket bucket, String aggregation) {
        JsonNode value = bucket.aggregations().path(aggregation).path(VALUE);
        return value.isNumber() ? value.asDouble() : 0;
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private JsonNode post(Map<String, Object> search) {
        try {
            return Objects.requireNonNull(client.post()
                    .uri(searchUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(search))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new MetricsSourceException("Elasticsearch answered " + failure.getStatusCode().value() + ": "
                                + error(failure.getBody().readAllBytes()), null);
                    })
                    .body(JsonNode.class), "Elasticsearch answered no body");
        } catch (ResourceAccessException e) {
            throw new MetricsSourceException("Elasticsearch at " + searchUrl + " did not answer", e);
        }
    }

    private JsonNode get(URI uri) {
        try {
            return Objects.requireNonNull(client.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new MetricsSourceException("Elasticsearch answered " + failure.getStatusCode().value() + ": "
                                + error(failure.getBody().readAllBytes()), null);
                    })
                    .body(JsonNode.class), "Elasticsearch answered no body");
        } catch (ResourceAccessException e) {
            throw new MetricsSourceException("Elasticsearch at " + uri + " did not answer", e);
        }
    }

    /**
     * The first root cause's reason, which tells more than a search's "all shards failed".
     */
    private String error(byte[] body) {
        try {
            JsonNode error = jsonMapper.readTree(body).path("error");
            JsonNode rootCause = error.path("root_cause").path(0).path("reason");
            return rootCause.isString() ? rootCause.asString() : error.path("reason").asString();
        } catch (RuntimeException notJson) {
            return new String(body, StandardCharsets.UTF_8).strip();
        }
    }

    private record Bucket(Instant start, long documents, JsonNode aggregations) {
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
