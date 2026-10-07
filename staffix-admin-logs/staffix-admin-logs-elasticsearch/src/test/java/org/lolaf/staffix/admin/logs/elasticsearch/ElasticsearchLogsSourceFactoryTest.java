package org.lolaf.staffix.admin.logs.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchLogsSourceFactoryTest {

    private final ElasticsearchLogsSourceFactory factory = new ElasticsearchLogsSourceFactory(RestClient.builder(), JsonMapper.builder().build());

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

    private static ElasticsearchLogsSettings settings(URI url, String apiKey, String username, String password) {
        return new ElasticsearchLogsSettings(url, Duration.ofSeconds(2), "logs-*.otel-*", apiKey, username, password);
    }
}
