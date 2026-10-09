package org.lolaf.staffix.admin.clickhouse;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Runs SQL through ClickHouse's HTTP interface, one JSON object per row, and tests the connection; a failure becomes
 * the caller's SPI exception.
 */
public final class ClickHouseClient {

    private static final String USER = "X-ClickHouse-User";
    private static final String KEY = "X-ClickHouse-Key";

    private final RestClient client;
    private final String rootUrl;
    private final String database;
    private final JsonMapper jsonMapper;
    private final BiFunction<String, Throwable, ? extends RuntimeException> failure;

    public ClickHouseClient(ClickHouseConnection connection, RestClient.Builder builder, JsonMapper jsonMapper,
                            BiFunction<String, Throwable, ? extends RuntimeException> failure) {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(HttpClientSettings.defaults()
                .withTimeouts(connection.getTimeout(), connection.getTimeout())
                .withRedirects(HttpRedirects.DONT_FOLLOW));
        builder.defaultHeader(USER, connection.getUsername());
        if (connection.getPassword() != null) {
            builder.defaultHeader(KEY, connection.getPassword());
        }
        this.client = builder.requestFactory(requestFactory).build();
        this.rootUrl = connection.getUrl().toString().replaceAll("/+$", "") + "/";
        this.database = connection.getDatabase();
        this.jsonMapper = jsonMapper;
        this.failure = failure;
    }

    /**
     * @throws IllegalArgumentException if the connection names no url, database or username
     */
    public static void check(ClickHouseConnection connection) {
        if (connection.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        if (connection.getDatabase() == null || connection.getDatabase().isBlank()) {
            throw new IllegalArgumentException("database is required");
        }
        if (connection.getUsername() == null || connection.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
    }

    /**
     * Runs a query, which also checks the credentials, then makes sure the database holds every table.
     */
    public void testConnection(Set<String> tables) {
        ClickHouseParameters parameters = new ClickHouseParameters();
        Set<String> found = query("SELECT name FROM system.tables WHERE database = currentDatabase() AND name IN ("
                + parameters.strings(tables) + ")", parameters).stream()
                .map(row -> row.path("name").asString())
                .collect(Collectors.toSet());
        List<String> missing = tables.stream().filter(table -> !found.contains(table)).sorted().toList();
        if (!missing.isEmpty()) {
            throw failure.apply("ClickHouse database " + database + " has no table " + String.join(", ", missing), null);
        }
    }

    /**
     * The rows of {@code sql}, which must not name a format.
     */
    public List<JsonNode> query(String sql, ClickHouseParameters parameters) {
        URI uri = URI.create(rootUrl + "?" + queryString(parameters.values()));
        String body;
        try {
            body = client.post()
                    .uri(uri)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(sql + " FORMAT JSONEachRow")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (sent, response) -> {
                        throw failure.apply("ClickHouse answered " + response.getStatusCode().value() + ": "
                                + error(response.getBody().readAllBytes()), null);
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw failure.apply("ClickHouse at " + rootUrl + " did not answer", e);
        }
        return Objects.requireNonNullElse(body, "").lines()
                .filter(line -> !line.isBlank())
                .map(jsonMapper::readTree)
                .toList();
    }

    private String queryString(Map<String, String> parameters) {
        StringBuilder query = new StringBuilder("database=").append(encoded(database))
                // 64-bit integers as JSON numbers rather than strings
                .append("&output_format_json_quote_64bit_integers=0");
        parameters.forEach((name, value) -> query.append("&param_").append(name).append('=').append(encoded(value)));
        return query.toString();
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * The exception's message, which ClickHouse sends as JSON for a JSON format and as text otherwise.
     */
    private String error(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8).strip();
        try {
            JsonNode exception = jsonMapper.readTree(text).path("exception");
            return exception.isString() ? exception.asString().strip() : text;
        } catch (RuntimeException notJson) {
            return text;
        }
    }
}
