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
package org.lolaf.staffix.admin.logs.loki;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.springframework.web.client.RestClient;

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
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LokiLogsSourceTest {

    private static final Instant FROM = Instant.parse("2026-10-02T08:00:00Z");
    private static final String TWO_STREAMS = """
            {"status":"success","data":{"resultType":"streams","result":[
             {"stream":{"fix_eid":"alpha","fix_sg":"beta","fix_sn":"trading","fix_md":"i","fix_mt":"8"},
              "values":[["1790928005000000123","8=FIX.4.4|35=8|"],["1790928001000000000","8=FIX.4.4|35=8|older"]]},
             {"stream":{"fix_eid":"alpha","fix_sg":"beta","fix_sn":"trading"},
              "values":[["1790928003000000000","Logon received"]]}]}}
            """;

    private HttpServer loki;
    private volatile Map<String, String> params;
    private volatile String tenant;
    private volatile int status = 200;
    private volatile String response = TWO_STREAMS;

    @BeforeEach
    void setUp() throws Exception {
        loki = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        loki.createContext("/loki/api/v1/query_range", exchange -> {
            params = Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            tenant = exchange.getRequestHeaders().getFirst("X-Scope-OrgID");
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", status == 200 ? "application/json" : "text/plain");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        loki.createContext("/loki/api/v1/labels", exchange -> {
            tenant = exchange.getRequestHeaders().getFirst("X-Scope-OrgID");
            byte[] body = (status == 200 ? "{\"status\":\"success\",\"data\":[\"fix_eid\"]}" : "no org id").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        loki.start();
    }

    @AfterEach
    void tearDown() {
        loki.stop(0);
    }

    @Test
    void mergesStreamsNewestFirstUpToTheLimit() {
        List<LogEntry> entries = source(null).query(query().limit(2).build());

        assertThat(params).containsEntry("start", "1790928000000000000")
                .containsEntry("end", "1790928600000000000")
                .containsEntry("limit", "2")
                .containsEntry("direction", "backward")
                .containsEntry("query", "{service_name=~\".+\"} | fix_eid=\"alpha\" | fix_sg=\"beta\" | fix_sn=\"trading\"");
        assertThat(entries).containsExactly(
                LogEntry.builder().timestamp(Instant.ofEpochSecond(1790928005, 123)).group("beta").sessionId("trading")
                        .type(LogType.IN).line("8=FIX.4.4|35=8|").build(),
                LogEntry.builder().timestamp(Instant.ofEpochSecond(1790928003)).group("beta").sessionId("trading")
                        .type(LogType.EVENT).line("Logon received").build());
        assertThat(tenant).isNull();
    }

    @Test
    void filtersTextTypesAndMessageTypes() {
        source(null).query(query().text("ALPHA \"US\"").type(LogType.OUT).type(LogType.IN).msgType("8").msgType("D").build());

        assertThat(params.get("query")).isEqualTo("{service_name=~\".+\"} |= \"ALPHA \\\"US\\\"\" | fix_eid=\"alpha\" | fix_sg=\"beta\""
                + " | fix_sn=\"trading\" | fix_md=\"i\" or fix_md=\"o\" | fix_mt=~\"8|D\"");
    }

    @Test
    void filtersEventsAsTheRecordsWithoutDirection() {
        source(null).query(query().type(LogType.EVENT).type(LogType.IN).build());

        assertThat(params.get("query")).endsWith(" | fix_md=\"\" or fix_md=\"i\"");
    }

    @Test
    void filtersAFieldValueWithASubstringThenAnchoredOnTheSeparators() {
        source(null).query(query().fieldValue("ALPHA-1.2").build());

        assertThat(params.get("query")).isEqualTo("{service_name=~\".+\"} |= \"=ALPHA-1.2\" |~ \"(^|[|\\\\x01])[0-9]+=(ALPHA-1\\\\.2)([|\\\\x01]|$)\""
                + " | fix_eid=\"alpha\" | fix_sg=\"beta\" | fix_sn=\"trading\"");
    }

    @Test
    void filtersSeveralFieldValuesWithTheRegexOnly() {
        source(null).query(query().fieldValue("BETA-2").fieldValue("ALPHA-1").build());

        assertThat(params.get("query")).startsWith("{service_name=~\".+\"} |~ \"(^|[|\\\\x01])[0-9]+=(ALPHA-1|BETA-2)([|\\\\x01]|$)\" | ");
    }

    @Test
    void matchesIndexedLabelsInTheStreamSelector() {
        source(Set.of(LokiLabel.FIX_EID, LokiLabel.FIX_MD), null).query(query().type(LogType.IN).msgType("8").build());

        assertThat(params.get("query")).isEqualTo("{service_name=~\".+\", fix_eid=\"alpha\", fix_md=~\"i\"}"
                + " | fix_sg=\"beta\" | fix_sn=\"trading\" | fix_mt=~\"8\"");
    }

    @Test
    void matchesEventsOfAnIndexedDirectionAsAnEmptyValue() {
        source(Set.of(LokiLabel.FIX_EID, LokiLabel.FIX_MD), null).query(query().type(LogType.EVENT).type(LogType.OUT).build());

        assertThat(params.get("query")).startsWith("{service_name=~\".+\", fix_eid=\"alpha\", fix_md=~\"|o\"}");
    }

    @Test
    void escapesRegexMetacharactersInMessageTypes() {
        source(null).query(LogQuery.builder().engineId("alpha").from(FROM).to(FROM.plusSeconds(600)).msgType("A.B").limit(10).build());

        assertThat(params.get("query")).endsWith("| fix_mt=~\"A\\\\.B\"");
    }

    @Test
    void sendsTheTenant() {
        source("alpha-tenant").query(query().build());

        assertThat(tenant).isEqualTo("alpha-tenant");
    }

    @Test
    void reportsLokiErrors() {
        status = 400;
        response = "parse error at line 1, col 25\n";

        assertThatThrownBy(() -> source(null).query(query().build()))
                .isInstanceOf(LogsSourceException.class)
                .hasMessage("Loki answered 400: parse error at line 1, col 25");
    }

    @Test
    void testsTheConnectionByListingTheLabelsOfTheTenant() {
        source("alpha-tenant").testConnection();

        assertThat(tenant).isEqualTo("alpha-tenant");
    }

    @Test
    void reportsAConnectionTestLokiRefuses() {
        status = 401;

        assertThatThrownBy(() -> source(null).testConnection())
                .isInstanceOf(LogsSourceException.class)
                .hasMessage("Loki answered 401: no org id");
    }

    @Test
    void reportsAnUnreachableLoki() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        LokiLogsSource unreachable = new LokiLogsSource(properties(URI.create("http://127.0.0.1:" + closedPort), Set.of(), null), RestClient.builder());

        assertThatThrownBy(() -> unreachable.query(query().build()))
                .isInstanceOf(LogsSourceException.class)
                .hasMessageContaining("did not answer");
    }

    private LokiLogsSource source(String tenant) {
        return source(Set.of(), tenant);
    }

    private LokiLogsSource source(Set<LokiLabel> indexedLabels, String tenant) {
        URI url = URI.create("http://127.0.0.1:" + loki.getAddress().getPort() + "/");
        return new LokiLogsSource(properties(url, indexedLabels, tenant), RestClient.builder());
    }

    private static LokiSettings properties(URI url, Set<LokiLabel> indexedLabels, String tenant) {
        return new LokiSettings(url, Duration.ofSeconds(2), "{service_name=~\".+\"}", indexedLabels, tenant);
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
