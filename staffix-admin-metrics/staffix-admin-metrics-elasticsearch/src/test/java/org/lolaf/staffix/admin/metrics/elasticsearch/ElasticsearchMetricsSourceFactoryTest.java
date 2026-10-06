package org.lolaf.staffix.admin.metrics.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchMetricsSourceFactoryTest {

    private final ElasticsearchMetricsSourceFactory factory = new ElasticsearchMetricsSourceFactory(RestClient.builder(), JsonMapper.builder().build());

    @Test
    void buildsASourcePerConnector() {
        assertThat(factory.create(settings(URI.create("http://alpha:9200"), null, null, null)))
                .isNotSameAs(factory.create(settings(URI.create("http://beta:9200"), null, null, null)));
    }

    @Test
    void requiresTheUrl() {
        assertThatThrownBy(() -> factory.create(settings(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
    }

    @Test
    void takesAnApiKeyOrAUsernameNotBoth() {
        assertThatThrownBy(() -> factory.create(settings(URI.create("http://alpha:9200"), "key", "staffix", "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey and username are exclusive");
    }

    @Test
    void requiresTheUsersPassword() {
        assertThatThrownBy(() -> factory.create(settings(URI.create("http://alpha:9200"), null, "staffix", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password is required with username");
    }

    private static ElasticsearchMetricsSettings settings(URI url, String apiKey, String username, String password) {
        return new ElasticsearchMetricsSettings(url, Duration.ofSeconds(2), "metrics-*.otel-*", apiKey, username, password, TimeUnit.MILLISECONDS, Duration.ofSeconds(10));
    }
}
