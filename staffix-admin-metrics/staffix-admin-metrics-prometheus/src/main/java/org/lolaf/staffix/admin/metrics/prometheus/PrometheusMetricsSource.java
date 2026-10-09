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
package org.lolaf.staffix.admin.metrics.prometheus;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads staffix's micrometer meters as Prometheus names them when they arrive through the OpenTelemetry collector
 * with {@code UnderscoreEscapingWithoutSuffixes}: {@code messages_read_latency_bucket}, {@code session_rtt_sum}...
 * Quantiles come from the timers' histograms, so only a timer publishing its percentile histogram has them.
 */
public class PrometheusMetricsSource implements MetricsSource {

    private static final String ENGINE = PrometheusLabel.FIX_EID.label();
    private static final String GROUP = PrometheusLabel.FIX_SG.label();
    private static final String SESSION = PrometheusLabel.FIX_SN.label();
    private static final Pattern NOT_IN_A_NAME = Pattern.compile("[^a-zA-Z0-9_:]");

    private final RestClient client;
    private final URI queryRangeUrl;
    private final URI buildInfoUrl;
    private final double timerUnitsPerSecond;
    private final Duration minRateWindow;
    private final JsonMapper jsonMapper;

    public PrometheusMetricsSource(PrometheusSettings properties, RestClient.Builder builder, JsonMapper jsonMapper) {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(HttpClientSettings.defaults()
                .withTimeouts(properties.getTimeout(), properties.getTimeout())
                .withRedirects(HttpRedirects.DONT_FOLLOW));
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
        String meter = name(query.getMeter());
        String selector = selector(query);
        Map<String, String> tagsByLabel = query.getSplitBy().stream().collect(Collectors.toMap(PrometheusMetricsSource::name, tag -> tag, (one, other) -> one, LinkedHashMap::new));
        String by = String.join(", ", tagsByLabel.keySet());
        Duration window = rateWindow(query);
        String range = window.toSeconds() + "s";
        return switch (query.getStatistic()) {
            case RATE -> queryRange(query, 1, tagsByLabel, window,
                    "sum by (" + by + ") (rate(" + meter + "_count{" + selector + "}[" + range + "]))");
            case QUANTILE -> queryRange(query, timerUnitsPerSecond(query), tagsByLabel, window,
                    "histogram_quantile(" + Objects.requireNonNullElse(query.getQuantile(), 0.5) + ", sum by (le" + (by.isEmpty() ? "" : ", " + by)
                    + ") (rate(" + meter + "_bucket{" + selector + "}[" + range + "])))");
            case MEAN -> queryRange(query, timerUnitsPerSecond(query), tagsByLabel, window,
                    "sum by (" + by + ") (rate(" + meter + "_sum{" + selector + "}[" + range + "]))"
                    + " / sum by (" + by + ") (rate(" + meter + "_count{" + selector + "}[" + range + "]))");
            case VALUE -> queryRange(query, query.getGaugeUnit() == null ? 1 : query.getGaugeUnit().convert(1, TimeUnit.SECONDS),
                    tagsByLabel, null, "avg by (" + by + ") (" + meter + "{" + selector + "})");
            case SHARE -> queryRange(query, 1, tagsByLabel, null, share(query, meter + "{" + selector + "}", by));
        };
    }

    /**
     * A subquery's point takes the last sample within reach, so the resolution only has to be as fine as the samples.
     * A sample reaches two exports (at least {@code minRateWindow}, its delivery delay) rather than the 5 minute
     * lookback, so a crashed engine, which sends no final status, shows as missing data at once.
     */
    private String share(MetricQuery query, String series, String by) {
        String value = number(Objects.requireNonNull(query.getValue(), "A share needs a value"));
        Duration step = query.getStep();
        Duration sampleInterval = query.getSampleInterval();
        if (sampleInterval == null) {
            return "avg_over_time((max by (" + by + ") (" + series + ") == bool " + value + ")[" + step.toSeconds() + "s:])";
        }
        Duration twoExports = sampleInterval.multipliedBy(2);
        Duration freshness = twoExports.compareTo(minRateWindow) > 0 ? twoExports : minRateWindow;
        String held = "(max by (" + by + ") (last_over_time(" + series + "[" + freshness.toMillis() + "ms])) == bool " + value + ")";
        Duration resolution = sampleInterval.compareTo(step) < 0 ? sampleInterval : step;
        return "sum_over_time(" + held + "[" + step.toSeconds() + "s:" + resolution.toMillis() + "ms]) / "
                + number((double) step.toMillis() / resolution.toMillis());
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    @Override
    public Duration getMinRateWindow() {
        return minRateWindow;
    }

    /**
     * As the collector's {@code UnderscoreEscapingWithoutSuffixes} writes a meter or a tag: every character Prometheus
     * does not allow in a name becomes an underscore.
     */
    static String name(String staffixName) {
        return NOT_IN_A_NAME.matcher(staffixName).replaceAll("_");
    }

    private static String selector(MetricQuery query) {
        StringBuilder selector = new StringBuilder(ENGINE).append('=').append(quote(query.getEngineId()));
        if (query.getGroup() != null) {
            selector.append(',').append(GROUP).append('=').append(quote(query.getGroup()));
        }
        if (query.getSessionId() != null) {
            selector.append(',').append(SESSION).append('=').append(quote(query.getSessionId()));
        }
        query.getTagFilters().forEach((tag, value) -> selector.append(',').append(name(tag)).append('=').append(quote(value)));
        return selector.toString();
    }

    private static String quote(String labelValue) {
        return '"' + labelValue.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    private Duration rateWindow(MetricQuery query) {
        Duration asked = Objects.requireNonNullElse(query.getRateWindow(), query.getStep());
        return asked.compareTo(minRateWindow) > 0 ? asked : minRateWindow;
    }

    private double timerUnitsPerSecond(MetricQuery query) {
        return query.getTimerBaseUnit() == null ? timerUnitsPerSecond : query.getTimerBaseUnit().convert(1, TimeUnit.SECONDS);
    }

    private List<MetricSeries> queryRange(MetricQuery query, double unitsPerSecond, Map<String, String> tagsByLabel,
                                          @Nullable Duration rateWindow, String promQl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", promQl);
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
        return series(Objects.requireNonNull(response, "Prometheus answered no body"), unitsPerSecond, tagsByLabel, rateWindow);
    }

    private String error(byte[] body) {
        try {
            return jsonMapper.readTree(body).path("error").asString();
        } catch (RuntimeException notJson) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static List<MetricSeries> series(JsonNode response, double unitsPerSecond, Map<String, String> tagsByLabel,
                                             @Nullable Duration rateWindow) {
        return response.path("data").path("result").valueStream()
                .map(result -> {
                    MetricSeries.MetricSeriesBuilder series = MetricSeries.builder().rateWindow(rateWindow);
                    result.path("metric").properties().forEach(label -> {
                        String tag = tagsByLabel.get(label.getKey());
                        if (tag != null) {
                            series.label(tag, label.getValue().asString());
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
