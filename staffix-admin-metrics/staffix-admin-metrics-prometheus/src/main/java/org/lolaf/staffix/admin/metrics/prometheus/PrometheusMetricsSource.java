package org.lolaf.staffix.admin.metrics.prometheus;

import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Reads staffix's micrometer meters as Prometheus names them when they arrive through the OpenTelemetry collector
 * with {@code UnderscoreEscapingWithoutSuffixes}: {@code messages_read_latency_bucket}, {@code session_rtt_sum}...
 * Latencies are quantiles of the timers' histograms, so the engines must publish percentile histograms.
 */
public class PrometheusMetricsSource implements MetricsSource {

    private static final String ENGINE = PrometheusLabel.FIX_EID.label();
    private static final String GROUP = PrometheusLabel.FIX_SG.label();
    private static final String SESSION = PrometheusLabel.FIX_SN.label();
    private static final String MESSAGE_TYPE = PrometheusLabel.FIX_MSG_TYPE.label();
    private static final String DIRECTION = PrometheusLabel.FIX_MSG_DIR.label();
    private static final Map<String, String> SERIES_LABELS = Map.of(
            MESSAGE_TYPE, MetricSeries.MESSAGE_TYPE,
            DIRECTION, MetricSeries.DIRECTION,
            GROUP, MetricSeries.GROUP,
            SESSION, MetricSeries.SESSION_ID);
    private static final String BY_MESSAGE = MESSAGE_TYPE + ", " + DIRECTION;
    private static final String BY_SESSION = GROUP + ", " + SESSION;
    private static final String MESSAGE_RATE_QUERY =
            "sum by (" + BY_MESSAGE + ") (rate(messages_read_latency_count{%1$s," + DIRECTION + "=\"in\"}[%2$s]))"
            + " or sum by (" + BY_MESSAGE + ") (rate(messages_write_latency_count{%1$s," + DIRECTION + "=\"out\"}[%2$s]))";
    private static final String ROUND_TRIP_TIME_QUERY = "sum by (" + BY_SESSION + ") (rate(session_rtt_sum{%1$s}[%2$s]))"
            + " / sum by (" + BY_SESSION + ") (rate(session_rtt_count{%1$s}[%2$s]))";
    private static final String CLOCK_OFFSET_QUERY = "avg by (" + BY_SESSION + ") (session_clock_offset{%1$s})";
    private static final double NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final RestClient client;
    private final URI queryRangeUrl;
    private final URI buildInfoUrl;
    private final double timerUnitsPerSecond;
    private final Duration minRateWindow;
    private final JsonMapper jsonMapper;

    public PrometheusMetricsSource(PrometheusSettings properties, RestClient.Builder builder, JsonMapper jsonMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build());
        requestFactory.setReadTimeout(properties.getTimeout());
        this.client = builder.requestFactory(requestFactory).build();
        String url = properties.getUrl().toString().replaceAll("/+$", "");
        this.queryRangeUrl = URI.create(url + "/api/v1/query_range");
        this.buildInfoUrl = URI.create(url + "/api/v1/status/buildinfo");
        this.timerUnitsPerSecond = properties.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
        this.minRateWindow = properties.getMinRateWindow();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void testConnection() {
        try {
            client.get()
                    .uri(buildInfoUrl)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new MetricsSourceException("Prometheus answered " + failure.getStatusCode().value() + ": "
                                + error(failure.getBody().readAllBytes()), null);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new MetricsSourceException("Prometheus at " + buildInfoUrl + " did not answer", e);
        }
    }

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        String selector = selector(query);
        String window = window(query);
        return switch (query.getKind()) {
            case MESSAGE_RATE -> queryRange(query, 1, MESSAGE_RATE_QUERY, selector, window);
            case READ_LATENCY -> latency(query, "messages_read_latency", "in", selector, window);
            case DECODING_LATENCY -> latency(query, "messages_decoding_latency", "in", selector, window);
            case WRITE_LATENCY -> latency(query, "messages_write_latency", "out", selector, window);
            case ENCODING_LATENCY -> latency(query, "messages_encoding_latency", "out", selector, window);
            case ROUND_TRIP_TIME -> queryRange(query, timerUnitsPerSecond(query), ROUND_TRIP_TIME_QUERY, selector, window);
            case CLOCK_OFFSET -> queryRange(query, NANOS_PER_SECOND, CLOCK_OFFSET_QUERY, selector, window);
        };
    }

    private List<MetricSeries> latency(MetricQuery query, String timer, String direction, String selector, String window) {
        double quantile = Objects.requireNonNullElse(query.getQuantile(), 0.5);
        return queryRange(query, timerUnitsPerSecond(query),
                "histogram_quantile(" + quantile + ", sum by (le, " + BY_MESSAGE + ") (rate(" + timer
                + "_bucket{%1$s," + DIRECTION + "=\"" + direction + "\"}[%2$s])))",
                selector, window);
    }

    private static String selector(MetricQuery query) {
        StringBuilder selector = new StringBuilder(ENGINE).append('=').append(quote(query.getEngineId()));
        if (query.getGroup() != null) {
            selector.append(',').append(GROUP).append('=').append(quote(query.getGroup()));
        }
        if (query.getSessionId() != null) {
            selector.append(',').append(SESSION).append('=').append(quote(query.getSessionId()));
        }
        return selector.toString();
    }

    private static String quote(String labelValue) {
        return '"' + labelValue.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    private String window(MetricQuery query) {
        Duration window = query.getStep().compareTo(minRateWindow) > 0 ? query.getStep() : minRateWindow;
        return window.toSeconds() + "s";
    }

    private double timerUnitsPerSecond(MetricQuery query) {
        return query.getTimerBaseUnit() == null ? timerUnitsPerSecond : query.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
    }

    private List<MetricSeries> queryRange(MetricQuery query, double unitsPerSecond, String promQl, String selector, String window) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", promQl.formatted(selector, window));
        form.add("start", Long.toString(query.getFrom().getEpochSecond()));
        form.add("end", Long.toString(query.getTo().getEpochSecond()));
        form.add("step", query.getStep().toSeconds() + "s");
        JsonNode response;
        try {
            response = client.post()
                    .uri(queryRangeUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new MetricsSourceException("Prometheus answered " + failure.getStatusCode().value() + ": "
                                + error(failure.getBody().readAllBytes()), null);
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new MetricsSourceException("Prometheus at " + queryRangeUrl + " did not answer", e);
        }
        return series(Objects.requireNonNull(response, "Prometheus answered no body"), unitsPerSecond);
    }

    private String error(byte[] body) {
        try {
            return jsonMapper.readTree(body).path("error").asString();
        } catch (RuntimeException notJson) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static List<MetricSeries> series(JsonNode response, double unitsPerSecond) {
        return response.path("data").path("result").valueStream()
                .map(result -> {
                    MetricSeries.MetricSeriesBuilder series = MetricSeries.builder();
                    result.path("metric").properties().forEach(label -> {
                        String name = SERIES_LABELS.get(label.getKey());
                        if (name != null) {
                            series.label(name, label.getValue().asString());
                        }
                    });
                    result.path("values").valueStream().forEach(sample -> {
                        double value = Double.parseDouble(sample.get(1).asString());
                        if (Double.isFinite(value)) {
                            series.point(new MetricPoint(instant(sample.get(0).asDouble()), value / unitsPerSecond));
                        }
                    });
                    return series.build();
                })
                .filter(series -> !series.getPoints().isEmpty())
                .toList();
    }

    private static Instant instant(double epochSeconds) {
        return Instant.ofEpochMilli(Math.round(epochSeconds * 1000));
    }
}
