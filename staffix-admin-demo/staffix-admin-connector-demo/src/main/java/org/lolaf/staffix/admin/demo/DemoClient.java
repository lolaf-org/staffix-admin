package org.lolaf.staffix.admin.demo;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Calls the simulator's demo endpoint; a failure becomes the caller's SPI exception.
 */
final class DemoClient {

    private final RestClient client;
    private final URI baseUrl;

    DemoClient(DemoSettings settings, RestClient.Builder builder) {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(HttpClientSettings.defaults()
                .withTimeouts(settings.getTimeout(), settings.getTimeout())
                .withRedirects(HttpRedirects.DONT_FOLLOW));
        this.client = builder.requestFactory(requestFactory).build();
        this.baseUrl = URI.create(settings.getUrl().toString().replaceAll("/+$", ""));
    }

    <E extends RuntimeException> JsonNode get(String path, Map<String, ?> query, BiFunction<String, Throwable, E> failure) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUri(baseUrl).path(path);
        query.forEach((name, value) -> {
            if (value != null) {
                uri.queryParam(name, value);
            }
        });
        URI target = uri.encode().build().toUri();
        try {
            return Objects.requireNonNull(client.get()
                    .uri(target)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw failure.apply("The simulator answered " + response.getStatusCode().value() + ": "
                                + new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).strip(), null);
                    })
                    .body(JsonNode.class), "The simulator answered no body");
        } catch (ResourceAccessException e) {
            throw failure.apply("The simulator at " + baseUrl + " did not answer", e);
        }
    }

    <E extends RuntimeException> void ping(BiFunction<String, Throwable, E> failure) {
        try {
            client.get().uri(baseUrl + "/demo/health").retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw failure.apply("The simulator answered " + response.getStatusCode().value(), null);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw failure.apply("The simulator at " + baseUrl + " did not answer", e);
        }
    }
}
