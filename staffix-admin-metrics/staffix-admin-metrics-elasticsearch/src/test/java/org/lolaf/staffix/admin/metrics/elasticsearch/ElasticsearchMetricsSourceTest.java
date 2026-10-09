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
package org.lolaf.staffix.admin.metrics.elasticsearch;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricStatistic;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchMetricsSourceTest {

    private static final Instant FROM = Instant.parse("2026-10-02T08:00:00Z");
    private static final long FROM_MILLIS = FROM.toEpochMilli();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer elasticsearch;
    private volatile String path;
    private volatile JsonNode search;
    private volatile String authorization;
    private volatile int status = 200;
    private volatile String response;
    private volatile String resolved = "{\"indices\":[],\"aliases\":[],\"data_streams\":[{\"name\":\"metrics-generic.otel-default\"}]}";

    @BeforeEach
    void setUp() throws Exception {
        elasticsearch = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        elasticsearch.createContext("/", exchange -> {
            path = exchange.getRequestURI().getRawPath();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String body;
            if (path.endsWith("/_search")) {
                search = JSON.readTree(exchange.getRequestBody().readAllBytes());
                body = response;
            } else if (path.startsWith("/_resolve/index/")) {
                body = resolved;
            } else {
                body = status == 200 ? "{\"name\":\"es\",\"version\":{\"number\":\"9.5.3\"}}" : response;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        elasticsearch.start();
    }

    @AfterEach
    void tearDown() {
        elasticsearch.stop(0);
    }

    @Test
    void ratesATimersRecordingsOverTheWindowSplitAndFilteredByItsTagsUpToTheNewestExport() {
        response = split("R", buckets(-10, 5, "{\"doc_count\":2,\"count\":{\"value\":10}}", "{\"doc_count\":2,\"count\":{\"value\":20}}",
                        "{\"doc_count\":2,\"count\":{\"value\":30}}", "{\"doc_count\":0,\"count\":{\"value\":0}}", "{\"doc_count\":0,\"count\":{\"value\":0}}"))
                + "," + split("S", buckets(-10, 5, "{\"doc_count\":0,\"count\":{\"value\":0}}"));
        response = "{\"aggregations\":{\"split\":{\"buckets\":[" + response + "]}}}";

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.RATE, "messages.read.latency", Duration.ofSeconds(5)));

        assertThat(path).isEqualTo("/metrics-*.otel-*/_search");
        assertThat(search.path("query").path("bool").path("filter").toString()).isEqualTo("["
                + "{\"term\":{\"fix.eid\":\"alpha\"}},"
                + "{\"term\":{\"fix.sg\":\"beta\"}},"
                + "{\"term\":{\"fix.sn\":\"trading\"}},"
                + "{\"term\":{\"fix.msg.dir\":\"in\"}},"
                + "{\"exists\":{\"field\":\"metrics.messages.read.latency\"}},"
                + "{\"range\":{\"@timestamp\":{\"gte\":" + (FROM_MILLIS - 10_000) + ",\"lt\":" + (FROM_MILLIS + 10_000) + ",\"format\":\"epoch_millis\"}}}]");
        JsonNode split = search.path("aggs").path("split");
        assertThat(split.path("terms").path("field").asString()).isEqualTo("fix.msg.type");
        JsonNode histogram = split.path("aggs").path("series");
        assertThat(histogram.path("date_histogram").path("fixed_interval").asString()).isEqualTo("5000ms");
        assertThat(histogram.path("date_histogram").path("extended_bounds").path("min").asLong()).isEqualTo(FROM_MILLIS - 10_000);
        assertThat(histogram.path("aggs").path("count").path("value_count").path("field").asString()).isEqualTo("metrics.messages.read.latency");
        assertThat(series).containsExactly(MetricSeries.builder()
                .label("fix.msg.type", "R")
                .point(new MetricPoint(FROM, 3))
                .point(new MetricPoint(FROM.plusSeconds(5), 5))
                .rateWindow(Duration.ofSeconds(10))
                .build());
    }

    @Test
    void ratesOverTheRequestedWindowInWholeSteps() {
        response = "{\"aggregations\":{\"series\":{\"buckets\":[]}}}";

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.RATE, "messages.read.latency", Duration.ofSeconds(5))
                .toBuilder().clearSplitBy().rateWindow(Duration.ofSeconds(12)).build());

        assertThat(search.path("query").path("bool").path("filter").get(5).path("range").path("@timestamp").path("gte").asLong())
                .isEqualTo(FROM_MILLIS - 15_000);
        assertThat(series).isEmpty();
    }

    @Test
    void tellsItsMinimumRateWindow() {
        assertThat(source(TimeUnit.MILLISECONDS).getMinRateWindow()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void readsQuantilesOncePerWindowInSeconds() {
        response = "{\"aggregations\":{\"series\":{\"buckets\":"
                + buckets(-10, 10, "{\"doc_count\":3,\"value\":{\"values\":{\"99.0\":250.0}}}", "{\"doc_count\":3,\"value\":{\"values\":{\"99.0\":500.0}}}",
                "{\"doc_count\":0,\"value\":{\"values\":{\"99.0\":null}}}") + "}}}";

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.QUANTILE, "messages.write.latency", Duration.ofSeconds(5))
                .toBuilder().clearSplitBy().build());

        JsonNode histogram = search.path("aggs").path("series");
        assertThat(histogram.path("date_histogram").path("fixed_interval").asString()).isEqualTo("10000ms");
        assertThat(histogram.path("aggs").path("value").path("percentiles").path("percents").toString()).isEqualTo("[99.0]");
        assertThat(series).containsExactly(MetricSeries.builder()
                .point(new MetricPoint(FROM, 250e-6))
                .point(new MetricPoint(FROM.plusSeconds(10), 500e-6))
                .rateWindow(Duration.ofSeconds(10))
                .build());
    }

    @Test
    void readsMeansOverTheWindowInTheEnginesTimerUnit() {
        response = "{\"aggregations\":{\"series\":{\"buckets\":"
                + buckets(-10, 5, "{\"doc_count\":1,\"count\":{\"value\":2},\"sum\":{\"value\":40}}", "{\"doc_count\":1,\"count\":{\"value\":2},\"sum\":{\"value\":80}}",
                "{\"doc_count\":1,\"count\":{\"value\":0},\"sum\":{\"value\":0}}") + "}}}";

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.MEAN, "session.rtt", Duration.ofSeconds(5))
                .toBuilder().clearSplitBy().timerBaseUnit(TimeUnit.MILLISECONDS).build());

        assertThat(search.path("aggs").path("series").path("aggs").path("sum").path("sum").path("field").asString()).isEqualTo("metrics.session.rtt");
        assertThat(series.getFirst().getPoints()).containsExactly(new MetricPoint(FROM, 0.03), new MetricPoint(FROM.plusSeconds(5), 0.04));
    }

    @Test
    void readsAGaugeInItsUnitAsAnAveragePerStep() {
        response = "{\"aggregations\":{\"series\":{\"buckets\":"
                + buckets(-5, 5, "{\"doc_count\":1,\"value\":{\"value\":1500}}", "{\"doc_count\":0,\"value\":{\"value\":null}}") + "}}}";

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.VALUE, "session.clock.offset", Duration.ofSeconds(5))
                .toBuilder().clearSplitBy().gaugeUnit(TimeUnit.MILLISECONDS).build());

        assertThat(search.path("aggs").path("series").path("aggs").path("value").path("avg").path("field").asString()).isEqualTo("metrics.session.clock.offset");
        assertThat(series).containsExactly(MetricSeries.builder().point(new MetricPoint(FROM, 1.5)).build());
    }

    @Test
    void sharesEachStepAmongTheSamplesItShouldHold() {
        response = "{\"aggregations\":{\"series\":{\"buckets\":"
                + buckets(-10, 10, "{\"doc_count\":2,\"held\":{\"doc_count\":1}}", "{\"doc_count\":1,\"held\":{\"doc_count\":1}}") + "}}}";

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.SHARE, "session.logon.status", Duration.ofSeconds(10))
                .toBuilder().clearSplitBy().clearTagFilters().value(1.0).sampleInterval(Duration.ofSeconds(5)).build());

        assertThat(search.path("aggs").path("series").path("aggs").path("held").path("filter").toString())
                .isEqualTo("{\"term\":{\"metrics.session.logon.status\":1.0}}");
        assertThat(series).containsExactly(MetricSeries.builder()
                .point(new MetricPoint(FROM, 0.5))
                .point(new MetricPoint(FROM.plusSeconds(10), 0.5))
                .build());
    }

    @Test
    void sharesAmongTheSamplesFoundWithoutASampleInterval() {
        response = "{\"aggregations\":{\"series\":{\"buckets\":"
                + buckets(-10, 10, "{\"doc_count\":2,\"held\":{\"doc_count\":1}}", "{\"doc_count\":1,\"held\":{\"doc_count\":1}}") + "}}}";

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.SHARE, "session.logon.status", Duration.ofSeconds(10))
                .toBuilder().clearSplitBy().clearTagFilters().value(1.0).build());

        assertThat(series.getFirst().getPoints()).extracting(MetricPoint::getValue).containsExactly(0.5, 1.0);
    }

    @Test
    void sendsTheApiKey() {
        response = "{\"aggregations\":{\"series\":{\"buckets\":[]}}}";

        source(TimeUnit.MILLISECONDS, "a2V5OnNlY3JldA==").query(messages(MetricStatistic.RATE, "messages.read.latency", Duration.ofSeconds(5)));

        assertThat(authorization).isEqualTo("ApiKey a2V5OnNlY3JldA==");
    }

    @Test
    void reportsTheRootCauseOfAnError() {
        status = 400;
        response = """
                {"error":{"root_cause":[{"type":"unsupported_aggregation_on_downsampled_index",
                 "reason":"Field [metrics.session.rtt] of type [aggregate_metric_double] is not supported for aggregation [percentiles]"}],
                 "type":"search_phase_execution_exception","reason":"all shards failed"},"status":400}
                """;

        assertThatThrownBy(() -> source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.QUANTILE, "session.rtt", Duration.ofSeconds(5))))
                .isInstanceOf(MetricsSourceException.class)
                .hasMessage("Elasticsearch answered 400: Field [metrics.session.rtt] of type [aggregate_metric_double] is not supported for aggregation [percentiles]");
    }

    @Test
    void testsTheConnectionAndTheIndex() {
        source(TimeUnit.MILLISECONDS).testConnection();

        assertThat(path).isEqualTo("/_resolve/index/metrics-*.otel-*");
    }

    @Test
    void reportsAnIndexMatchingNothing() {
        resolved = "{\"indices\":[],\"aliases\":[],\"data_streams\":[]}";

        assertThatThrownBy(() -> source(TimeUnit.MILLISECONDS).testConnection())
                .isInstanceOf(MetricsSourceException.class)
                .hasMessage("Elasticsearch has no index or data stream matching metrics-*.otel-*");
    }

    @Test
    void reportsAnUnreachableElasticsearch() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        ElasticsearchMetricsSource unreachable = new ElasticsearchMetricsSource(settings(URI.create("http://127.0.0.1:" + closedPort),
                TimeUnit.MILLISECONDS, null), RestClient.builder(), JSON);

        assertThatThrownBy(() -> unreachable.query(messages(MetricStatistic.RATE, "messages.read.latency", Duration.ofSeconds(5))))
                .isInstanceOf(MetricsSourceException.class)
                .hasMessageContaining("did not answer");
    }

    /**
     * Date histogram buckets from {@code FROM + firstOffset} seconds, one per {@code interval} seconds.
     */
    private static String buckets(int firstOffset, int interval, String... buckets) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < buckets.length; i++) {
            long key = FROM_MILLIS + (firstOffset + (long) i * interval) * 1000;
            json.append(i == 0 ? "" : ",").append("{\"key\":").append(key).append(',').append(buckets[i].substring(1));
        }
        return json.append(']').toString();
    }

    private static String split(String key, String buckets) {
        return "{\"key\":\"" + key + "\",\"doc_count\":4,\"series\":{\"buckets\":" + buckets + "}}";
    }

    private ElasticsearchMetricsSource source(TimeUnit timerBaseUnit) {
        return source(timerBaseUnit, null);
    }

    private ElasticsearchMetricsSource source(TimeUnit timerBaseUnit, String apiKey) {
        return new ElasticsearchMetricsSource(settings(URI.create("http://127.0.0.1:" + elasticsearch.getAddress().getPort() + "/"), timerBaseUnit, apiKey),
                RestClient.builder(), JSON);
    }

    private static ElasticsearchMetricsSettings settings(URI url, TimeUnit timerBaseUnit, String apiKey) {
        return new ElasticsearchMetricsSettings(url, Duration.ofSeconds(2), "metrics-*.otel-*", apiKey, null, null, timerBaseUnit, Duration.ofSeconds(10));
    }

    private static MetricQuery messages(MetricStatistic statistic, String meter, Duration step) {
        return MetricQuery.builder()
                .engineId("alpha")
                .group("beta")
                .sessionId("trading")
                .meter(meter)
                .statistic(statistic)
                .quantile(statistic == MetricStatistic.QUANTILE ? 0.99 : null)
                .splitBy("fix.msg.type")
                .tagFilter("fix.msg.dir", "in")
                .from(FROM)
                .to(FROM.plusSeconds(10))
                .step(step)
                .build();
    }
}
