package org.lolaf.staffix.admin.elasticsearch;

import lombok.Value;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchClientTest {

    private static final URI URL = URI.create("http://alpha:9200");

    @Test
    void acceptsAnApiKeyAUserOrNoCredentials() {
        assertThatCode(() -> {
            ElasticsearchClient.check(connection(URL, "key", null, null));
            ElasticsearchClient.check(connection(URL, null, "staffix", "secret"));
            ElasticsearchClient.check(connection(URL, null, null, null));
        }).doesNotThrowAnyException();
    }

    @Test
    void requiresTheUrl() {
        assertThatThrownBy(() -> ElasticsearchClient.check(connection(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
    }

    @Test
    void takesAnApiKeyOrAUsernameNotBoth() {
        assertThatThrownBy(() -> ElasticsearchClient.check(connection(URL, "key", "staffix", "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey and username are exclusive");
    }

    @Test
    void requiresTheUsersPassword() {
        assertThatThrownBy(() -> ElasticsearchClient.check(connection(URL, null, "staffix", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password is required with username");
    }

    private static ElasticsearchConnection connection(URI url, String apiKey, String username, String password) {
        return new Connection(url, Duration.ofSeconds(2), "logs-*.otel-*", apiKey, username, password);
    }

    @Value
    private static class Connection implements ElasticsearchConnection {
        URI url;
        Duration timeout;
        String index;
        String apiKey;
        String username;
        String password;
    }
}
