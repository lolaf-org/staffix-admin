package org.lolaf.staffix.admin.elasticsearch;

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
    void checksTheConnection() {
        assertThatThrownBy(() -> factory.create(settings(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
    }

    private static ElasticsearchMetricsSettings settings(URI url, String apiKey, String username, String password) {
        return new ElasticsearchMetricsSettings(url, Duration.ofSeconds(2), "metrics-*.otel-*", apiKey, username, password, TimeUnit.MILLISECONDS, Duration.ofSeconds(10));
    }
}
