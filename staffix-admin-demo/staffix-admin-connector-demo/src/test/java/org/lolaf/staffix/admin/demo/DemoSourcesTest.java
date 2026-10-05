package org.lolaf.staffix.admin.demo;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricStatistic;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoSourcesTest {

    private static final Instant FROM = Instant.parse("2026-10-03T08:00:00Z");

    private HttpServer simulator;
    private volatile String query;
    private DemoSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        simulator = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        simulator.createContext("/demo/", exchange -> {
            query = exchange.getRequestURI().getQuery();
            String body = switch (exchange.getRequestURI().getPath()) {
                case "/demo/metrics" -> "[{\"labels\":{\"messageType\":\"D\",\"direction\":\"in\"},\"points\":[{\"timestamp\":\"2026-10-03T08:00:00Z\",\"value\":12.5}],\"rateWindow\":20}]";
                case "/demo/logs" -> "[{\"timestamp\":\"2026-10-03T08:00:01Z\",\"group\":\"alpha\",\"sessionId\":\"trading\",\"type\":\"OUT\",\"line\":\"8=FIX.4.4|35=D|\"}]";
                default -> "{\"status\":404,\"detail\":\"Unknown engine 'omega'\"}";
            };
            int status = body.startsWith("[") ? 200 : 404;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        simulator.start();
        settings = new DemoSettings(URI.create("http://127.0.0.1:" + simulator.getAddress().getPort()), Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        simulator.stop(0);
    }

    @Test
    void readsTheSimulatorsSeries() {
        List<MetricSeries> series = new DemoMetricsSourceFactory(RestClient.builder()).create(settings).query(MetricQuery.builder()
                .engineId("paris-prod").group("alpha").meter("messages.read.latency").statistic(MetricStatistic.RATE)
                .splitBy("fix.msg.type").tagFilter("fix.msg.dir", "in").from(FROM).to(FROM.plusSeconds(60))
                .step(Duration.ofSeconds(5)).rateWindow(Duration.ofSeconds(20)).build());

        assertThat(series).singleElement().satisfies(one -> {
            assertThat(one.getLabels()).containsEntry("messageType", "D").containsEntry("direction", "in");
            assertThat(one.getPoints()).containsExactly(new MetricPoint(FROM, 12.5));
            assertThat(one.getRateWindow()).isEqualTo(Duration.ofSeconds(20));
        });
        assertThat(query).contains("engineId=paris-prod", "group=alpha", "meter=messages.read.latency", "statistic=RATE", "splitBy=fix.msg.type", "tagFilters=fix.msg.dir=in", "step=5", "rateWindow=20");
    }

    @Test
    void readsTheSimulatorsLogs() {
        List<LogEntry> entries = new DemoLogsSourceFactory(RestClient.builder()).create(settings).query(LogQuery.builder()
                .engineId("paris-prod").type(LogType.OUT).msgType("D").from(FROM).to(FROM.plusSeconds(60)).limit(10).build());

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(LogType.OUT);
            assertThat(entry.getGroup()).isEqualTo("alpha");
            assertThat(entry.getLine()).isEqualTo("8=FIX.4.4|35=D|");
        });
        assertThat(query).contains("types=OUT", "msgTypes=D", "limit=10");
    }

    @Test
    void reportsWhatTheSimulatorRefuses() {
        assertThatThrownBy(() -> new DemoLogsSourceFactory(RestClient.builder()).create(settings).testConnection())
                .isInstanceOf(LogsSourceException.class)
                .hasMessageContaining("404");
    }
}
