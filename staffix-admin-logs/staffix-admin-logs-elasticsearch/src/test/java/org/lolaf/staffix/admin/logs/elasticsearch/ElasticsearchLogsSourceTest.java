package org.lolaf.staffix.admin.logs.elasticsearch;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSourceException;
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
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchLogsSourceTest {

    private static final Instant FROM = Instant.parse("2026-10-02T08:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TWO_HITS = """
            {"took":3,"timed_out":false,"hits":{"max_score":null,"hits":[
             {"_index":".ds-logs-generic.otel-default","_id":"a","fields":{"@timestamp":["2026-10-02T08:00:05.000000123Z"],
              "fix.sg":["beta"],"fix.sn":["trading"],"fix.log.type":["in"],"body.text":["8=FIX.4.4|35=8|"]}},
             {"_index":".ds-logs-generic.otel-default","_id":"b","fields":{"@timestamp":["2026-10-02T08:00:03Z"],
              "fix.sg":["beta"],"fix.sn":["trading"],"fix.log.type":["event"],"body.text":["Logon received"]}}]}}
            """;

    private HttpServer elasticsearch;
    private volatile String path;
    private volatile JsonNode search;
    private volatile String authorization;
    private volatile int status = 200;
    private volatile String response = TWO_HITS;
    private volatile String resolved = "{\"indices\":[],\"aliases\":[],\"data_streams\":[{\"name\":\"logs-generic.otel-default\"}]}";

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
    void readsTheNewestEntriesOfTheSession() {
        List<LogEntry> entries = source(null, null, null).query(query().limit(2).build());

        assertThat(path).isEqualTo("/logs-*.otel-*/_search");
        assertThat(search.path("size").asInt()).isEqualTo(2);
        assertThat(search.path("sort").toString()).isEqualTo("[{\"@timestamp\":{\"order\":\"desc\"}}]");
        assertThat(search.path("query").path("bool").path("filter").toString()).isEqualTo("["
                + "{\"term\":{\"fix.eid\":\"alpha\"}},"
                + "{\"term\":{\"fix.sg\":\"beta\"}},"
                + "{\"term\":{\"fix.sn\":\"trading\"}},"
                + "{\"range\":{\"@timestamp\":{\"gte\":\"2026-10-02T08:00:00Z\",\"lt\":\"2026-10-02T08:10:00Z\",\"format\":\"strict_date_optional_time_nanos\"}}}]");
        assertThat(entries).containsExactly(
                LogEntry.builder().timestamp(Instant.ofEpochSecond(1790928005, 123)).group("beta").sessionId("trading")
                        .type(LogType.IN).line("8=FIX.4.4|35=8|").build(),
                LogEntry.builder().timestamp(Instant.ofEpochSecond(1790928003)).group("beta").sessionId("trading")
                        .type(LogType.EVENT).line("Logon received").build());
        assertThat(authorization).isNull();
    }

    @Test
    void filtersTextTypesAndMessageTypes() {
        source(null, null, null).query(LogQuery.builder().engineId("alpha").from(FROM).to(FROM.plusSeconds(600))
                .text("ALPHA \"US\"").type(LogType.OUT).type(LogType.IN).msgType("D").msgType("8").limit(10).build());

        assertThat(search.path("query").path("bool").path("filter").toString()).isEqualTo("["
                + "{\"term\":{\"fix.eid\":\"alpha\"}},"
                + "{\"terms\":{\"fix.log.type\":[\"in\",\"out\"]}},"
                + "{\"terms\":{\"fix.msg.type\":[\"8\",\"D\"]}},"
                + "{\"match_phrase\":{\"body.text\":\"ALPHA \\\"US\\\"\"}},"
                + "{\"range\":{\"@timestamp\":{\"gte\":\"2026-10-02T08:00:00Z\",\"lt\":\"2026-10-02T08:10:00Z\",\"format\":\"strict_date_optional_time_nanos\"}}}]");
    }

    @Test
    void readsTheConfiguredIndex() {
        new ElasticsearchLogsSource(new ElasticsearchLogsSettings(url(), Duration.ofSeconds(2), "fix-logs,other-logs", null, null, null),
                RestClient.builder(), JSON).query(query().build());

        assertThat(path).isEqualTo("/fix-logs,other-logs/_search");
    }

    @Test
    void sendsTheApiKey() {
        source("a2V5OnNlY3JldA==", null, null).query(query().build());

        assertThat(authorization).isEqualTo("ApiKey a2V5OnNlY3JldA==");
    }

    @Test
    void sendsTheUsersCredentials() {
        source(null, "staffix", "s3cret").query(query().build());

        assertThat(authorization).isEqualTo("Basic " + Base64.getEncoder().encodeToString("staffix:s3cret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void reportsTheRootCauseOfAnError() {
        status = 400;
        response = """
                {"error":{"root_cause":[{"type":"query_shard_exception","reason":"failed to create query: For input string"}],
                 "type":"search_phase_execution_exception","reason":"all shards failed"},"status":400}
                """;

        assertThatThrownBy(() -> source(null, null, null).query(query().build()))
                .isInstanceOf(LogsSourceException.class)
                .hasMessage("Elasticsearch answered 400: failed to create query: For input string");
    }

    @Test
    void testsTheConnectionWithTheCredentials() {
        source(null, "staffix", "s3cret").testConnection();

        assertThat(path).isEqualTo("/_resolve/index/logs-*.otel-*");
        assertThat(authorization).startsWith("Basic ");
    }

    @Test
    void reportsAConnectionTestElasticsearchRefuses() {
        status = 401;
        response = "{\"error\":{\"type\":\"security_exception\",\"reason\":\"unable to authenticate user [staffix]\"},\"status\":401}";

        assertThatThrownBy(() -> source(null, "staffix", "wrong").testConnection())
                .isInstanceOf(LogsSourceException.class)
                .hasMessage("Elasticsearch answered 401: unable to authenticate user [staffix]");
    }

    @Test
    void reportsAnIndexMatchingNothing() {
        resolved = "{\"indices\":[],\"aliases\":[],\"data_streams\":[]}";

        assertThatThrownBy(() -> source(null, null, null).testConnection())
                .isInstanceOf(LogsSourceException.class)
                .hasMessage("Elasticsearch has no index or data stream matching logs-*.otel-*");
    }

    @Test
    void reportsAnUnreachableElasticsearch() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        ElasticsearchLogsSource unreachable = new ElasticsearchLogsSource(new ElasticsearchLogsSettings(
                URI.create("http://127.0.0.1:" + closedPort), Duration.ofSeconds(2), "logs-*.otel-*", null, null, null), RestClient.builder(), JSON);

        assertThatThrownBy(() -> unreachable.query(query().build()))
                .isInstanceOf(LogsSourceException.class)
                .hasMessageContaining("did not answer");
    }

    private ElasticsearchLogsSource source(String apiKey, String username, String password) {
        return new ElasticsearchLogsSource(new ElasticsearchLogsSettings(url(), Duration.ofSeconds(2), "logs-*.otel-*", apiKey, username, password),
                RestClient.builder(), JSON);
    }

    private URI url() {
        return URI.create("http://127.0.0.1:" + elasticsearch.getAddress().getPort() + "/");
    }

    private static LogQuery.LogQueryBuilder query() {
        return LogQuery.builder()
                .engineId("alpha")
                .group("beta")
                .sessionId("trading")
                .from(FROM)
                .to(FROM.plusSeconds(600))
                .limit(500);
    }
}
