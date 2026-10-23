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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricStatistic;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseMetricsSourceTest {

    private static final Instant FROM = Instant.parse("2026-10-02T08:00:00Z");
    private static final long FROM_MILLIS = FROM.toEpochMilli();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(p\\d+):[A-Za-z0-9]+}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer clickHouse;
    private volatile Map<String, String> parameters;
    private volatile String sql;
    private volatile String response = "";

    @BeforeEach
    void setUp() throws Exception {
        clickHouse = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        clickHouse.createContext("/", exchange -> {
            parameters = Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            sql = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        clickHouse.start();
    }

    @AfterEach
    void tearDown() {
        clickHouse.stop(0);
    }

    @Test
    void ratesATimersIncreaseBetweenExportsOverTheWindowSplitByItsTagsUpToTheNewestExport() {
        response = rows(
                "{\"l0\":\"D\",\"bucket\":" + (FROM_MILLIS - 10_000) + ",\"count\":10,\"samples\":2}",
                "{\"l0\":\"D\",\"bucket\":" + (FROM_MILLIS - 5_000) + ",\"count\":20,\"samples\":2}",
                "{\"l0\":\"D\",\"bucket\":" + FROM_MILLIS + ",\"count\":30,\"samples\":2}");

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.RATE, Duration.ofSeconds(5)));

        assertThat(bound())
                .contains("if(mapContains(Attributes, 'fix.mt'), Attributes['fix.mt'], ResourceAttributes['fix.mt']) AS l0, ")
                .contains("toInt64(Count) - toInt64(prevCount) AS dCount")
                .contains("FROM 'otel_metrics_histogram' WHERE MetricName = 'messages.read.latency' "
                        + "AND TimeUnix >= toDateTime('" + (FROM.getEpochSecond() - 310) + "') AND TimeUnix <= toDateTime('" + (FROM.getEpochSecond() + 10) + "') "
                        + "AND (Attributes['fix.eid'] = 'alpha' OR ResourceAttributes['fix.eid'] = 'alpha') "
                        + "AND (Attributes['fix.sg'] = 'beta' OR ResourceAttributes['fix.sg'] = 'beta') "
                        + "AND (Attributes['fix.sn'] = 'trading' OR ResourceAttributes['fix.sn'] = 'trading') "
                        + "AND (Attributes['fix.md'] = 'i' OR ResourceAttributes['fix.md'] = 'i')")
                .contains("PARTITION BY ResourceAttributes, Attributes, StartTimeUnix ORDER BY TimeUnix")
                .contains("WHERE (n > 1 OR StartTimeUnix >= toDateTime('" + (FROM.getEpochSecond() - 310) + "')) AND bucket >= '" + (FROM_MILLIS - 10_000) + "'")
                .endsWith("GROUP BY l0, bucket FORMAT JSONEachRow");
        assertThat(series).containsExactly(MetricSeries.builder()
                .label("fix.mt", "D")
                .point(new MetricPoint(FROM, 3))
                .point(new MetricPoint(FROM.plusSeconds(5), 5))
                .rateWindow(Duration.ofSeconds(10))
                .build());
    }

    @Test
    void ratesACountersIncreaseBetweenExportsOfItsTotal() {
        response = rows(
                "{\"l0\":\"venue-x\",\"bucket\":" + (FROM_MILLIS - 10_000) + ",\"increase\":25,\"samples\":1}",
                "{\"l0\":\"venue-x\",\"bucket\":" + (FROM_MILLIS - 5_000) + ",\"increase\":25,\"samples\":1}",
                "{\"l0\":\"venue-x\",\"bucket\":" + FROM_MILLIS + ",\"increase\":15,\"samples\":1}");

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(MetricQuery.builder()
                .engineId("alpha-bank")
                .meter("quotes.received")
                .statistic(MetricStatistic.COUNTER_RATE)
                .splitBy("venue")
                .from(FROM)
                .to(FROM.plusSeconds(10))
                .step(Duration.ofSeconds(5))
                .build());

        assertThat(bound())
                .contains("Value - prevValue AS dValue")
                .contains("lagInFrame(Value) OVER w AS prevValue")
                .contains("FROM 'otel_metrics_sum' WHERE MetricName = 'quotes.received' ")
                .contains("PARTITION BY ResourceAttributes, Attributes, StartTimeUnix ORDER BY TimeUnix")
                .contains("WHERE (n > 1 OR StartTimeUnix >= toDateTime('" + (FROM.getEpochSecond() - 310) + "'))")
                .endsWith("GROUP BY l0, bucket FORMAT JSONEachRow");
        assertThat(series).containsExactly(MetricSeries.builder()
                .label("venue", "venue-x")
                .point(new MetricPoint(FROM, 5))
                .point(new MetricPoint(FROM.plusSeconds(5), 4))
                .rateWindow(Duration.ofSeconds(10))
                .build());
    }

    @Test
    void readsMeansInTheEnginesTimerUnit() {
        response = rows(
                "{\"l0\":\"D\",\"bucket\":" + (FROM_MILLIS - 10_000) + ",\"count\":2,\"sum\":40,\"samples\":1}",
                "{\"l0\":\"D\",\"bucket\":" + (FROM_MILLIS - 5_000) + ",\"count\":2,\"sum\":80,\"samples\":1}");

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.MEAN, Duration.ofSeconds(5))
                .toBuilder().timerBaseUnit(TimeUnit.MILLISECONDS).build());

        assertThat(series.getFirst().getPoints()).containsExactly(new MetricPoint(FROM, 0.03));
    }

    @Test
    void readsQuantilesOncePerWindowInSeconds() {
        response = rows("{\"l0\":\"D\",\"bucket\":" + (FROM_MILLIS - 10_000) + ",\"bounds\":[10,20,30],\"buckets\":[0,4,4,2],\"samples\":2}");

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.QUANTILE, Duration.ofSeconds(5))
                .toBuilder().quantile(0.5).build());

        assertThat(parameters.values()).contains("10000");
        assertThat(series.getFirst().getPoints()).containsExactly(new MetricPoint(FROM, 22.5e-6));
        assertThat(series.getFirst().getRateWindow()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void interpolatesAQuantileInItsBucketAndAnswersTheHighestBoundForTheOverflow() {
        var row = JSON.readTree("{\"bounds\":[10,20,30],\"buckets\":[0,4,4,2]}");

        assertThat(ClickHouseMetricsSource.quantile(0.5, row)).isEqualTo(22.5);
        assertThat(ClickHouseMetricsSource.quantile(0.2, row)).isEqualTo(15.0);
        assertThat(ClickHouseMetricsSource.quantile(0.99, row)).isEqualTo(30.0);
        assertThat(ClickHouseMetricsSource.quantile(0.5, JSON.readTree("{\"bounds\":[10],\"buckets\":[0,0]}"))).isNull();
    }

    @Test
    void readsAGaugeInItsUnitAsAnAveragePerStep() {
        response = rows("{\"bucket\":" + (FROM_MILLIS - 5_000) + ",\"value\":1500,\"samples\":1}");

        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(messages(MetricStatistic.VALUE, Duration.ofSeconds(5)).toBuilder()
                .meter("session.clock.offset").clearSplitBy().clearTagFilters().gaugeUnit(TimeUnit.MILLISECONDS).build());

        assertThat(bound()).contains("avg(Value) AS value, count() AS samples FROM 'otel_metrics_gauge' WHERE MetricName = 'session.clock.offset'")
                .endsWith("GROUP BY bucket FORMAT JSONEachRow");
        assertThat(series).containsExactly(MetricSeries.builder().point(new MetricPoint(FROM, 1.5)).build());
    }

    @Test
    void sharesEachStepAmongTheSamplesItShouldHold() {
        response = rows("{\"bucket\":" + (FROM_MILLIS - 10_000) + ",\"held\":1,\"samples\":2}",
                "{\"bucket\":" + FROM_MILLIS + ",\"held\":2,\"samples\":2}");

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.SHARE, Duration.ofSeconds(10)).toBuilder()
                .meter("session.logon.status").clearSplitBy().clearTagFilters().value(1.0).sampleInterval(Duration.ofSeconds(5)).build());

        assertThat(bound()).contains("countIf(Value = '1.0') AS held");
        assertThat(series.getFirst().getPoints()).containsExactly(new MetricPoint(FROM, 0.5), new MetricPoint(FROM.plusSeconds(10), 1.0));
    }

    @Test
    void sharesAmongTheSamplesFoundWithoutASampleInterval() {
        response = rows("{\"bucket\":" + (FROM_MILLIS - 10_000) + ",\"held\":1,\"samples\":2}");

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(messages(MetricStatistic.SHARE, Duration.ofSeconds(10)).toBuilder()
                .meter("session.logon.status").clearSplitBy().clearTagFilters().value(1.0).build());

        assertThat(series.getFirst().getPoints()).containsExactly(new MetricPoint(FROM, 0.5));
    }

    @Test
    void tellsItsMinimumRateWindow() {
        assertThat(source(TimeUnit.MILLISECONDS).getMinRateWindow()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void testsTheConnectionByItsTables() {
        response = rows("{\"name\":\"otel_metrics_histogram\"}", "{\"name\":\"otel_metrics_gauge\"}", "{\"name\":\"otel_metrics_sum\"}");

        source(TimeUnit.MILLISECONDS).testConnection();

        assertThat(bound()).contains("name IN ('otel_metrics_gauge', 'otel_metrics_histogram', 'otel_metrics_sum')");
    }

    private static String rows(String... rows) {
        return String.join("\n", rows) + "\n";
    }

    /**
     * The SQL with each parameter's value in place, quoted, so a test reads the query ClickHouse runs.
     */
    private String bound() {
        Matcher matcher = PLACEHOLDER.matcher(sql);
        StringBuilder bound = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(bound, Matcher.quoteReplacement("'" + parameters.get("param_" + matcher.group(1)) + "'"));
        }
        return matcher.appendTail(bound).toString();
    }

    private ClickHouseMetricsSource source(TimeUnit timerBaseUnit) {
        URI url = URI.create("http://127.0.0.1:" + clickHouse.getAddress().getPort());
        return new ClickHouseMetricsSource(new ClickHouseMetricsSettings(url, Duration.ofSeconds(2), "otel", "otel_metrics_histogram",
                "otel_metrics_gauge", "otel_metrics_sum", "default", null, timerBaseUnit, Duration.ofSeconds(10)), RestClient.builder(), JSON);
    }

    private static MetricQuery messages(MetricStatistic statistic, Duration step) {
        return MetricQuery.builder()
                .engineId("alpha")
                .group("beta")
                .sessionId("trading")
                .meter("messages.read.latency")
                .statistic(statistic)
                .splitBy("fix.mt")
                .tagFilter("fix.md", "i")
                .from(FROM)
                .to(FROM.plusSeconds(10))
                .step(step)
                .build();
    }
}
