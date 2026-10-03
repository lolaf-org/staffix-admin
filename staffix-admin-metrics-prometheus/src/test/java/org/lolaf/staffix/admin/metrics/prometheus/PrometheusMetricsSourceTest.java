package org.lolaf.staffix.admin.metrics.prometheus;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
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
             {"metric":{"fix_msg_dir":"in","fix_msg_type":"R"},"values":[[1790928000,"250"],[1790928005.5,"NaN"],[1790928010,"500"]]},
             {"metric":{"fix_msg_dir":"out","fix_msg_type":"S"},"values":[[1790928000,"NaN"]]}]}}
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
    void readsMessageRatesOfBothDirectionsAndDropsMissingPoints() {
        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(query(MetricKind.MESSAGE_RATE, Duration.ofSeconds(5)));

        assertThat(form.get("query")).isEqualTo(
                "sum by (fix_msg_type, fix_msg_dir) (rate(messages_read_latency_count{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\",fix_msg_dir=\"in\"}[60s]))"
                + " or sum by (fix_msg_type, fix_msg_dir) (rate(messages_write_latency_count{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\",fix_msg_dir=\"out\"}[60s]))");
        assertThat(form).containsEntry("start", "1790928000").containsEntry("end", "1790928600").containsEntry("step", "5s");
        assertThat(series).containsExactly(MetricSeries.builder()
                .label(MetricSeries.DIRECTION, "in")
                .label(MetricSeries.MESSAGE_TYPE, "R")
                .point(new MetricPoint(FROM, 250))
                .point(new MetricPoint(FROM.plusSeconds(10), 500))
                .build());
    }

    @Test
    void readsLatencyQuantilesInSeconds() {
        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(query(MetricKind.WRITE_LATENCY, Duration.ofMinutes(2)));

        assertThat(form.get("query")).isEqualTo("histogram_quantile(0.99, sum by (le, fix_msg_type, fix_msg_dir) (rate("
                + "messages_write_latency_bucket{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\",fix_msg_dir=\"out\"}[120s])))");
        assertThat(series.getFirst().getPoints()).extracting(MetricPoint::getValue).containsExactly(250e-6, 500e-6);
    }

    @Test
    void prefersTheTimerUnitTheQueryKnowsFromTheEngine() {
        List<MetricSeries> series = source(TimeUnit.MICROSECONDS).query(query(MetricKind.WRITE_LATENCY, Duration.ofMinutes(2))
                .toBuilder().timerBaseUnit(TimeUnit.MILLISECONDS).build());

        assertThat(series.getFirst().getPoints()).extracting(MetricPoint::getValue).containsExactly(0.25, 0.5);
    }

    @Test
    void readsClockOffsetInSecondsWhateverTheTimerUnit() {
        response = """
                {"status":"success","data":{"resultType":"matrix","result":[
                 {"metric":{"fix_sg":"beta","fix_sn":"trading"},"values":[[1790928000,"-1500"]]}]}}
                """;

        List<MetricSeries> series = source(TimeUnit.MILLISECONDS).query(query(MetricKind.CLOCK_OFFSET, Duration.ofSeconds(5)));

        assertThat(form.get("query")).isEqualTo("avg by (fix_sg, fix_sn) (session_clock_offset{fix_eid=\"alpha\",fix_sg=\"beta\",fix_sn=\"trading\"})");
        assertThat(series).containsExactly(MetricSeries.builder()
                .label(MetricSeries.GROUP, "beta")
                .label(MetricSeries.SESSION_ID, "trading")
                .point(new MetricPoint(FROM, -1.5e-6))
                .build());
    }

    @Test
    void escapesLabelValues() {
        source(TimeUnit.MILLISECONDS).query(MetricQuery.builder()
                .engineId("al\"pha\\")
                .kind(MetricKind.CLOCK_OFFSET)
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

        assertThatThrownBy(() -> source(TimeUnit.MILLISECONDS).query(query(MetricKind.MESSAGE_RATE, Duration.ofSeconds(5))))
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

        assertThatThrownBy(() -> unreachable.query(query(MetricKind.MESSAGE_RATE, Duration.ofSeconds(5))))
                .isInstanceOf(MetricsSourceException.class)
                .hasMessageContaining("did not answer");
    }

    private PrometheusMetricsSource source(TimeUnit timerBaseUnit) {
        URI url = URI.create("http://127.0.0.1:" + prometheus.getAddress().getPort() + "/");
        return new PrometheusMetricsSource(properties(url, timerBaseUnit), RestClient.builder(), JsonMapper.builder().build());
    }

    private static PrometheusSettings properties(URI url, TimeUnit timerBaseUnit) {
        return new PrometheusSettings(url, Duration.ofSeconds(2), timerBaseUnit, Duration.ofMinutes(1));
    }

    private static MetricQuery query(MetricKind kind, Duration step) {
        return MetricQuery.builder()
                .engineId("alpha")
                .group("beta")
                .sessionId("trading")
                .kind(kind)
                .quantile(kind.isLatency() ? 0.99 : null)
                .from(FROM)
                .to(FROM.plusSeconds(600))
                .step(step)
                .build();
    }
}
