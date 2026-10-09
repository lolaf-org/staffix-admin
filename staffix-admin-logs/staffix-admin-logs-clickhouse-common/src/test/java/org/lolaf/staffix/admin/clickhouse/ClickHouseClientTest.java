package org.lolaf.staffix.admin.clickhouse;

import com.sun.net.httpserver.HttpServer;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseClientTest {

    private HttpServer clickHouse;
    private volatile Map<String, String> parameters;
    private volatile String sql;
    private volatile String user;
    private volatile String key;
    private volatile int status = 200;
    private volatile String response = "";

    @BeforeEach
    void setUp() throws Exception {
        clickHouse = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        clickHouse.createContext("/", exchange -> {
            parameters = Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            sql = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            user = exchange.getRequestHeaders().getFirst("X-ClickHouse-User");
            key = exchange.getRequestHeaders().getFirst("X-ClickHouse-Key");
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
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
    void bindsEveryValueOnTheServerAndReadsOneRowPerLine() {
        response = "{\"n\":1791540389580695912,\"g\":\"alpha\"}\n{\"n\":2,\"g\":\"beta\"}\n";
        ClickHouseParameters bound = new ClickHouseParameters();
        String placeholder = bound.string("a+b & c=d\\e");

        List<JsonNode> rows = client().query("SELECT n, g FROM t WHERE x = " + placeholder, bound);

        assertThat(sql).isEqualTo("SELECT n, g FROM t WHERE x = {p0:String} FORMAT JSONEachRow");
        assertThat(parameters)
                .containsEntry("database", "otel")
                .containsEntry("output_format_json_quote_64bit_integers", "0")
                .containsEntry("param_p0", "a+b & c=d\\\\e");
        assertThat(user).isEqualTo("staffix");
        assertThat(key).isEqualTo("secret");
        assertThat(rows).extracting(row -> row.path("n").asLong()).containsExactly(1791540389580695912L, 2L);
    }

    @Test
    void reportsTheExceptionClickHouseAnswers() {
        status = 404;
        response = "{\"exception\": \"Code: 60. DB::Exception: Unknown table expression identifier 'nope'. (UNKNOWN_TABLE)\"}";

        assertThatThrownBy(() -> client().query("SELECT 1 FROM nope", new ClickHouseParameters()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ClickHouse answered 404: Code: 60. DB::Exception: Unknown table expression identifier 'nope'. (UNKNOWN_TABLE)");
    }

    @Test
    void testsThatTheDatabaseHoldsEveryTable() {
        response = "{\"name\":\"otel_logs\"}\n";

        assertThatThrownBy(() -> client().testConnection(Set.of("otel_logs", "otel_traces")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ClickHouse database otel has no table otel_traces");
        assertThat(sql).contains("name IN ({p0:String}, {p1:String})");
    }

    @Test
    void requiresTheUrlTheDatabaseAndTheUsername() {
        assertThatThrownBy(() -> ClickHouseClient.check(new Connection(null, Duration.ofSeconds(1), "otel", "staffix", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
        assertThatThrownBy(() -> ClickHouseClient.check(new Connection(URI.create("http://alpha:8123"), Duration.ofSeconds(1), " ", "staffix", null)))
                .hasMessage("database is required");
        assertThatThrownBy(() -> ClickHouseClient.check(new Connection(URI.create("http://alpha:8123"), Duration.ofSeconds(1), "otel", "", null)))
                .hasMessage("username is required");
    }

    private ClickHouseClient client() {
        URI url = URI.create("http://127.0.0.1:" + clickHouse.getAddress().getPort() + "/");
        return new ClickHouseClient(new Connection(url, Duration.ofSeconds(2), "otel", "staffix", "secret"), RestClient.builder(),
                JsonMapper.builder().build(), IllegalStateException::new);
    }

    @Value
    private static class Connection implements ClickHouseConnection {
        URI url;
        Duration timeout;
        String database;
        String username;
        String password;
    }
}
