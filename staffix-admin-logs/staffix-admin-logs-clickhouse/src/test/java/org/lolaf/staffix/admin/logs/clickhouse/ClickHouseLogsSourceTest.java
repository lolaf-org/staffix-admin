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
package org.lolaf.staffix.admin.logs.clickhouse;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSourceException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseLogsSourceTest {

    private static final Instant FROM = Instant.parse("2026-10-02T08:00:00Z");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(p\\d+):[A-Za-z0-9]+}");

    private HttpServer clickHouse;
    private volatile Map<String, String> parameters;
    private volatile String sql;
    private volatile int status = 200;
    private volatile String response = """
            {"nanos":1790928005123456789,"sg":"beta","sn":"trading","md":"o","line":"8=FIX.4.4|35=D|11=ALPHA-1|"}
            {"nanos":1790928004000000000,"sg":"beta","sn":"trading","md":"","line":"Logon received"}
            """;

    @BeforeEach
    void setUp() throws Exception {
        clickHouse = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        clickHouse.createContext("/", exchange -> {
            parameters = Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            sql = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
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
    void readsTheNewestEntriesOfTheSessionToTheNanosecond() {
        List<LogEntry> entries = source().query(query().limit(2).build());

        assertThat(bound()).isEqualTo("SELECT toUnixTimestamp64Nano(Timestamp) AS nanos, "
                + "if(mapContains(LogAttributes, 'fix.sg'), LogAttributes['fix.sg'], ResourceAttributes['fix.sg']) AS sg, "
                + "if(mapContains(LogAttributes, 'fix.sn'), LogAttributes['fix.sn'], ResourceAttributes['fix.sn']) AS sn, "
                + "if(mapContains(LogAttributes, 'fix.md'), LogAttributes['fix.md'], ResourceAttributes['fix.md']) AS md, "
                + "Body AS line FROM 'otel_logs' "
                + "WHERE Timestamp >= fromUnixTimestamp64Nano('1790928000000000000') AND Timestamp < fromUnixTimestamp64Nano('1790928600000000000') "
                + "AND (LogAttributes['fix.eid'] = 'alpha' OR ResourceAttributes['fix.eid'] = 'alpha') "
                + "AND (LogAttributes['fix.sg'] = 'beta' OR ResourceAttributes['fix.sg'] = 'beta') "
                + "AND (LogAttributes['fix.sn'] = 'trading' OR ResourceAttributes['fix.sn'] = 'trading') "
                + "ORDER BY Timestamp DESC LIMIT '2' FORMAT JSONEachRow");
        assertThat(parameters).containsEntry("database", "otel");
        assertThat(entries).containsExactly(
                LogEntry.builder().timestamp(Instant.parse("2026-10-02T08:00:05.123456789Z")).group("beta").sessionId("trading")
                        .type(LogType.OUT).line("8=FIX.4.4|35=D|11=ALPHA-1|").build(),
                LogEntry.builder().timestamp(FROM.plusSeconds(4)).group("beta").sessionId("trading")
                        .type(LogType.EVENT).line("Logon received").build());
    }

    @Test
    void filtersTypesAndMessageTypes() {
        source().query(LogQuery.builder().engineId("alpha").from(FROM).to(FROM.plusSeconds(600))
                .type(LogType.OUT).type(LogType.IN).msgType("D").msgType("8").limit(10).build());

        assertThat(bound())
                .contains("AND if(mapContains(LogAttributes, 'fix.md'), LogAttributes['fix.md'], ResourceAttributes['fix.md']) IN ('i', 'o')")
                .contains("AND if(mapContains(LogAttributes, 'fix.mt'), LogAttributes['fix.mt'], ResourceAttributes['fix.mt']) IN ('8', 'D')")
                .doesNotContain("fix.sg'] = ");
    }

    @Test
    void filtersEventsAsTheRecordsWithoutDirection() {
        source().query(query().type(LogType.EVENT).type(LogType.IN).build());

        assertThat(bound()).contains("AND if(mapContains(LogAttributes, 'fix.md'), LogAttributes['fix.md'], ResourceAttributes['fix.md']) IN ('', 'i')");
    }

    @Test
    void looksTextUpByItsWholeTokensThenMatchesItAsIs() {
        source().query(query().text("LPHA \"US\" 55=AAP").build());

        assertThat(bound()).contains("AND (hasToken(lower(Body), 'us') AND hasToken(lower(Body), '55')) "
                + "AND position(Body, 'LPHA \"US\" 55=AAP') > 0");
    }

    @Test
    void matchesTextWithoutAWholeTokenAsIs() {
        source().query(query().text("ALPH").build());

        assertThat(bound()).doesNotContain("hasToken").contains("AND position(Body, 'ALPH') > 0");
    }

    @Test
    void looksFieldValuesUpByTheirTokensThenMatchesWholeFields() {
        source().query(query().fieldValue("ALPHA-1").fieldValue("q.7").build());

        assertThat(bound()).contains("AND ((hasToken(lower(Body), 'alpha') AND hasToken(lower(Body), '1')) "
                + "OR (hasToken(lower(Body), 'q') AND hasToken(lower(Body), '7'))) "
                + "AND match(Body, '(^|[|\\x01])[0-9]+=(ALPHA-1|q\\.7)([|\\x01]|$)')");
    }

    @Test
    void readsTheConfiguredDatabaseAndTable() {
        new ClickHouseLogsSource(new ClickHouseLogsSettings(url(), Duration.ofSeconds(2), "fix", "fix_logs", "default", null),
                RestClient.builder(), JsonMapper.builder().build()).query(query().build());

        assertThat(parameters).containsEntry("database", "fix");
        assertThat(bound()).contains("FROM 'fix_logs'");
    }

    @Test
    void testsTheConnectionByItsTable() {
        response = "{\"name\":\"otel_logs\"}\n";

        source().testConnection();

        assertThat(bound()).contains("FROM system.tables").contains("name IN ('otel_logs')");
    }

    @Test
    void reportsClickHousesException() {
        status = 403;
        response = "{\"exception\": \"Code: 516. DB::Exception: staffix: Authentication failed. (AUTHENTICATION_FAILED)\"}";

        assertThatThrownBy(() -> source().query(query().build()))
                .isInstanceOf(LogsSourceException.class)
                .hasMessage("ClickHouse answered 403: Code: 516. DB::Exception: staffix: Authentication failed. (AUTHENTICATION_FAILED)");
    }

    /**
     * The SQL with each parameter's value in place, quoted, so a test reads the query ClickHouse runs.
     */
    private String bound() {
        Matcher matcher = PLACEHOLDER.matcher(sql);
        StringBuilder bound = new StringBuilder();
        while (matcher.find()) {
            String value = parameters.get("param_" + matcher.group(1)).replace("\\\\", "\\");
            matcher.appendReplacement(bound, Matcher.quoteReplacement("'" + value + "'"));
        }
        return matcher.appendTail(bound).toString();
    }

    private ClickHouseLogsSource source() {
        return new ClickHouseLogsSource(new ClickHouseLogsSettings(url(), Duration.ofSeconds(2), "otel", "otel_logs", "default", null),
                RestClient.builder(), JsonMapper.builder().build());
    }

    private URI url() {
        return URI.create("http://127.0.0.1:" + clickHouse.getAddress().getPort());
    }

    private static LogQuery.LogQueryBuilder query() {
        return LogQuery.builder().engineId("alpha").group("beta").sessionId("trading").from(FROM).to(FROM.plusSeconds(600)).limit(100);
    }
}
