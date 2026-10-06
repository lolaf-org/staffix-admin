package org.lolaf.staffix.admin.elasticsearch;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Searches the connection's index and tests the connection; a failure becomes the caller's SPI exception.
 */
final class ElasticsearchClient {

    private final RestClient client;
    private final URI rootUrl;
    private final URI searchUrl;
    private final URI resolveUrl;
    private final String index;
    private final JsonMapper jsonMapper;
    private final BiFunction<String, Throwable, ? extends RuntimeException> failure;

    ElasticsearchClient(ElasticsearchConnection connection, RestClient.Builder builder, JsonMapper jsonMapper,
                        BiFunction<String, Throwable, ? extends RuntimeException> failure) {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(HttpClientSettings.defaults()
                .withTimeouts(connection.getTimeout(), connection.getTimeout())
                .withRedirects(HttpRedirects.DONT_FOLLOW));
        if (connection.getApiKey() != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + connection.getApiKey());
        } else if (connection.getUsername() != null) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(connection.getUsername(), Objects.requireNonNullElse(connection.getPassword(), "")));
        }
        this.client = builder.requestFactory(requestFactory).build();
        String url = connection.getUrl().toString().replaceAll("/+$", "");
        this.index = connection.getIndex();
        this.rootUrl = URI.create(url + "/");
        this.searchUrl = UriComponentsBuilder.fromUriString(url).pathSegment(index, "_search").encode().build().toUri();
        this.resolveUrl = UriComponentsBuilder.fromUriString(url).pathSegment("_resolve", "index", index).encode().build().toUri();
        this.jsonMapper = jsonMapper;
        this.failure = failure;
    }

    /**
     * @throws IllegalArgumentException if the connection names no url, or credentials that do not go together
     */
    static void check(ElasticsearchConnection connection) {
        if (connection.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        if (connection.getApiKey() != null && connection.getUsername() != null) {
            throw new IllegalArgumentException("apiKey and username are exclusive");
        }
        if (connection.getUsername() != null && connection.getPassword() == null) {
            throw new IllegalArgumentException("password is required with username");
        }
    }

    /**
     * Reads the cluster's info, which also checks the credentials, then makes sure the index matches something.
     */
    void testConnection() {
        call(client.get().uri(rootUrl), rootUrl);
        JsonNode resolved = call(client.get().uri(resolveUrl), resolveUrl);
        if (resolved.path("indices").isEmpty() && resolved.path("aliases").isEmpty() && resolved.path("data_streams").isEmpty()) {
            throw failure.apply("Elasticsearch has no index or data stream matching " + index, null);
        }
    }

    JsonNode search(Map<String, Object> search) {
        return call(client.post().uri(searchUrl).contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(search)), searchUrl);
    }

    private JsonNode call(RestClient.RequestHeadersSpec<?> request, URI uri) {
        try {
            return Objects.requireNonNull(request.retrieve()
                    .onStatus(HttpStatusCode::isError, (sent, response) -> {
                        throw failure.apply("Elasticsearch answered " + response.getStatusCode().value() + ": "
                                + error(response.getBody().readAllBytes()), null);
                    })
                    .body(JsonNode.class), "Elasticsearch answered no body");
        } catch (ResourceAccessException e) {
            throw failure.apply("Elasticsearch at " + uri + " did not answer", e);
        }
    }

    /**
     * The first root cause's reason, which tells more than a search's "all shards failed".
     */
    private String error(byte[] body) {
        try {
            JsonNode error = jsonMapper.readTree(body).path("error");
            JsonNode rootCause = error.path("root_cause").path(0).path("reason");
            return rootCause.isString() ? rootCause.asString() : error.path("reason").asString();
        } catch (RuntimeException notJson) {
            return new String(body, StandardCharsets.UTF_8).strip();
        }
    }
}
