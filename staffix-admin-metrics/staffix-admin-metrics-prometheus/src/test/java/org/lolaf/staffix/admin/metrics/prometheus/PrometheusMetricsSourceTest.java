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
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrometheusMetricsSourceTest {

    private static final Instant FROM = Instant.parse("2026-10-02T08:00:00Z");
    private static final String TWO_POINTS = """
            {"status":"success","data":{"resultType":"matrix","result":[
             {"metric":{"fix_md":"i","fix_mt":"R"},"values":[[1790928000,"250"],[1790928005.5,"NaN"],[1790928010,"500"]]},
             {"metric":{"fix_md":"o","fix_mt":"S"},"values":[[1790928000,"NaN"]]}]}}
            """;

    private HttpServer prometheus;
    private volatile Map<String, String> form;
    private volatile int status = 200;
    private volatile String response = TWO_POINTS;

    @BeforeEach
    void setUp() throws Exception {
        prometheus = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        prometheus.createContext("/api/v1/query_range", exchange -> {
            form = Arrays.stream(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        prometheus.createContext("/api/v1/status/buildinfo", exchange -> {
            byte[] body = (status == 200 ? "{\"status\":\"success\",\"data\":{\"version\":\"3.5.0\"}}"
                    : "{\"status\":\"error\",\"error\":\"unauthorized\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        prometheus.start();
    }

    @AfterEach
    void tearDown() {
        prometheus.stop(0);
    }

    @Test
    void readsATimersRateSplitAndFilteredByItsTagsAndDropsMissingPoints() {
        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.RATE, "messages.read.latency", "i", Duration.ofSeconds(5)));

        assertThat(form.get("query")).isEqualTo(
                "sum by (fix_mt, fix_md) (rate(messages_read_latency_count{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\",fix_md=\"i\"}[20s]))");
        assertThat(form).containsEntry("start", "1790928000").containsEntry("end", "1790928600").containsEntry("step", "5s");
        assertThat(series).containsExactly(MetricSeries.builder()
                .label("fix.md", "i")
                .label("fix.mt", "R")
                .point(new MetricPoint(FROM, 250))
                .point(new MetricPoint(FROM.plusSeconds(10), 500))
                .rateWindow(Duration.ofSeconds(20))
                .build());
    }

    @Test
    void ratesOverTheRequestedWindow() {
        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.RATE, "messages.read.latency", "i", Duration.ofSeconds(5))
                .toBuilder().rateWindow(Duration.ofSeconds(30)).build());

        assertThat(form.get("query")).contains("[30s]").doesNotContain("[20s]");
        assertThat(series).extracting(MetricSeries::getRateWindow).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void widensARequestedWindowToTheMinimum() {
        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.RATE, "messages.read.latency", "i", Duration.ofSeconds(5))
                .toBuilder().rateWindow(Duration.ofSeconds(10)).build());

        assertThat(form.get("query")).contains("[20s]").doesNotContain("[10s]");
        assertThat(series).extracting(MetricSeries::getRateWindow).containsExactly(Duration.ofSeconds(20));
    }

    @Test
    void tellsItsMinimumRateWindow() {
        assertThat(source(TimeUnit.MILLISECONDS).getMinRateWindow()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void readsLatencyQuantilesInSeconds() {
        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.QUANTILE, "messages.write.latency", "o", Duration.ofMinutes(2)));

        assertThat(form.get("query")).isEqualTo("histogram_quantile(0.99, sum by (le, fix_mt, fix_md) (rate("
                + "messages_write_latency_bucket{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\",fix_md=\"o\"}[120s])))");
        assertThat(series.getFirst().getPoints()).extracting(MetricPoint::getValue).containsExactly(250e-6, 500e-6);
        assertThat(series.getFirst().getRateWindow()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void prefersTheTimerUnitTheQueryKnowsFromTheEngine() {
        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.QUANTILE, "messages.write.latency", "o", Duration.ofMinutes(2))
                .toBuilder().timerBaseUnit(TimeUnit.MILLISECONDS).build());

        assertThat(series.getFirst().getPoints()).extracting(MetricPoint::getValue).containsExactly(0.25, 0.5);
    }

    @Test
    void readsClockOffsetInSecondsWhateverTheTimerUnit() {
        response = """
                {"status":"success","data":{"resultType":"matrix","result":[
                 {"metric":{"fix_sg":"beta","fix_sn":"trading"},"values":[[1790928000,"-1500"]]}]}}
                """;

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(perSession("session.clock.offset", MetricStatistic.VALUE)
                .toBuilder().gaugeUnit(TimeUnit.NANOSECONDS).build());

        assertThat(form.get("query")).isEqualTo("avg by (fix_sg, fix_sn) (session_clock_offset{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\"})");
        assertThat(series).containsExactly(MetricSeries.builder()
                .label("fix.sg", "beta")
                .label("fix.sn", "trading")
                .point(new MetricPoint(FROM, -1.5e-6))
                .build());
    }

    @Test
    void readsAGaugeWithoutUnitAsIs() {
        response = """
                {"status":"success","data":{"resultType":"matrix","result":[
                 {"metric":{"fix_sg":"beta","fix_sn":"trading"},"values":[[1790928000,"1"]]}]}}
                """;

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(perSession("session.logon.status", MetricStatistic.VALUE));

        assertThat(series.getFirst().getPoints()).extracting(MetricPoint::getValue).containsExactly(1.0);
        assertThat(series.getFirst().getRateWindow()).isNull();
    }

    @Test
    void readsTheShareOfEachStepAGaugeHeldAValueFromItsSamples() {
        response = """
                {"status":"success","data":{"resultType":"matrix","result":[
                 {"metric":{"fix_sg":"beta","fix_sn":"trading"},"values":[[1790928000,"0.75"]]}]}}
                """;

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(perSession("session.logon.status", MetricStatistic.SHARE)
                .toBuilder().value(1.0).step(Duration.ofMinutes(1)).sampleInterval(Duration.ofSeconds(15)).build());

        assertThat(form.get("query")).isEqualTo("sum_over_time((max by (fix_sg, fix_sn) (last_over_time(session_logon_status{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\"}[30000ms]))"
                + " == bool 1)[60s:15000ms]) / 4");
        assertThat(series.getFirst().getPoints()).extracting(MetricPoint::getValue).containsExactly(0.75);
    }

    @Test
    void keepsAShareSampleNoLessThanTheMinimumRateWindow() {
        source(TimeUnit.MILLISECONDS).query(perSession("session.logon.status", MetricStatistic.SHARE)
                .toBuilder().value(1.0).step(Duration.ofMinutes(1)).sampleInterval(Duration.ofSeconds(1)).build());

        assertThat(form.get("query")).contains("[20000ms])) == bool 1)");
    }

    @Test
    void samplesAShareNoFinerThanItsStep() {
        source(TimeUnit.MILLISECONDS).query(perSession("session.logon.status", MetricStatistic.SHARE)
                .toBuilder().value(0.0).sampleInterval(Duration.ofSeconds(15)).build());

        assertThat(form.get("query")).endsWith(" == bool 0)[5s:5000ms]) / 1");
    }

    @Test
    void sharesAmongTheSamplesFoundWithoutASampleInterval() {
        source(TimeUnit.MILLISECONDS).query(perSession("session.logon.status", MetricStatistic.SHARE).toBuilder().value(2.0).build());

        assertThat(form.get("query")).startsWith("avg_over_time((max by (fix_sg, fix_sn) (session_logon_status{")
                .endsWith(" == bool 2)[5s:])");
    }

    @Test
    void readsACustomTimersMeanNamingItsTagsAsPrometheusDoes() {
        response = """
                {"status":"success","data":{"resultType":"matrix","result":[
                 {"metric":{"trade_venue":"xpar"},"values":[[1790928000,"2000"]]}]}}
                """;

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(MetricQuery.builder()
                .engineId("alpha")
                .meter("orders.pricing-time")
                .statistic(MetricStatistic.MEAN)
                .splitBy("trade-venue")
                .from(FROM)
                .to(FROM.plusSeconds(600))
                .step(Duration.ofMinutes(1))
                .build());

        assertThat(form.get("query")).isEqualTo("sum by (trade_venue) (rate(orders_pricing_time_sum{fix_eid=\"alpha\"}[60s]))"
                + " / sum by (trade_venue) (rate(orders_pricing_time_count{fix_eid=\"alpha\"}[60s]))");
        assertThat(series).singleElement().satisfies(one -> {
            assertThat(one.getLabels()).containsExactly(Map.entry("trade-venue", "xpar"));
            assertThat(one.getPoints()).extracting(MetricPoint::getValue).containsExactly(2e-3);
        });
    }

    @Test
    void readsAQuantileOverTheWholeScopeWithoutSplit() {
        source(TimeUnit.MICROSECONDS).query(MetricQuery.builder()
                .engineId("alpha")
                .meter("session.rtt")
                .statistic(MetricStatistic.QUANTILE)
                .quantile(0.5)
                .from(FROM)
                .to(FROM.plusSeconds(600))
                .step(Duration.ofMinutes(1))
                .build());

        assertThat(form.get("query")).isEqualTo("histogram_quantile(0.5, sum by (le) (rate(session_rtt_bucket{fix_eid=\"alpha\"}[60s])))");
    }

    @Test
    void escapesLabelValues() {
        source(TimeUnit.MILLISECONDS).query(MetricQuery.builder()
                .engineId("al\"pha\\")
                .meter("session.clock.offset")
                .statistic(MetricStatistic.VALUE)
                .splitBy("fix.sg")
                .splitBy("fix.sn")
                .from(FROM)
                .to(FROM.plusSeconds(600))
                .step(Duration.ofSeconds(5))
                .build());

        assertThat(form.get("query")).isEqualTo("avg by (fix_sg, fix_sn) (session_clock_offset{fix_eid=\"al\\\"pha\\\\\"})");
    }

    @Test
    void reportsPrometheusErrors() {
        status = 400;
        response = "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"parse error\"}";

        assertThatThrownBy(() -> source(TimeUnit.MILLISECONDS).query(perSession("session.rtt", MetricStatistic.MEAN)))
                .isInstanceOf(MetricsSourceException.class)
                .hasMessage("Prometheus answered 400: parse error");
    }

    @Test
    void testsTheConnectionWithTheBuildInfo() {
        source(TimeUnit.MILLISECONDS).testConnection();
    }

    @Test
    void reportsAConnectionTestPrometheusRefuses() {
        status = 401;

        assertThatThrownBy(() -> source(TimeUnit.MILLISECONDS).testConnection())
                .isInstanceOf(MetricsSourceException.class)
                .hasMessage("Prometheus answered 401: unauthorized");
    }

    @Test
    void reportsAnUnreachablePrometheus() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        PrometheusMetricsSource unreachable = new PrometheusMetricsSource(properties(URI.create("http://127.0.0.1:" + closedPort), TimeUnit.MILLISECONDS),
                RestClient.builder(), JsonMapper.builder().build());

        assertThatThrownBy(() -> unreachable.query(perSession("session.rtt", MetricStatistic.MEAN)))
                .isInstanceOf(MetricsSourceException.class)
                .hasMessageContaining("did not answer");
    }

    private PrometheusMetricsSource source(TimeUnit timerBaseUnit) {
        URI url = URI.create("http://127.0.0.1:" + prometheus.getAddress().getPort() + "/");
        return new PrometheusMetricsSource(properties(url, timerBaseUnit), RestClient.builder(), JsonMapper.builder().build());
    }

    private static PrometheusSettings properties(URI url, TimeUnit timerBaseUnit) {
        return new PrometheusSettings(url, Duration.ofSeconds(2), timerBaseUnit, Duration.ofSeconds(20));
    }

    private static MetricQuery messages(MetricStatistic statistic, String meter, String direction, Duration step) {
        return MetricQuery.builder()
                .engineId("alpha")
                .group("beta")
                .sessionId("trading")
                .meter(meter)
                .statistic(statistic)
                .quantile(statistic == MetricStatistic.QUANTILE ? 0.99 : null)
                .splitBy("fix.mt")
                .splitBy("fix.md")
                .tagFilter("fix.md", direction)
                .from(FROM)
                .to(FROM.plusSeconds(600))
                .step(step)
                .build();
    }

    private static MetricQuery perSession(String meter, MetricStatistic statistic) {
        return MetricQuery.builder()
                .engineId("alpha")
                .group("beta")
                .sessionId("trading")
                .meter(meter)
                .statistic(statistic)
                .splitBy("fix.sg")
                .splitBy("fix.sn")
                .from(FROM)
                .to(FROM.plusSeconds(600))
                .step(Duration.ofSeconds(5))
                .build();
    }
}
