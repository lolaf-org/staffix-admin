package org.lolaf.staffix.admin.logs.clickhouse;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseLogsSourceFactoryTest {

    private final ClickHouseLogsSourceFactory factory = new ClickHouseLogsSourceFactory(RestClient.builder(), JsonMapper.builder().build());

    @Test
    void buildsASourcePerConnector() {
        assertThat(factory.create(settings(URI.create("http://alpha:8123"))))
                .isNotSameAs(factory.create(settings(URI.create("http://beta:8123"))));
    }

    @Test
    void checksTheConnection() {
        assertThatThrownBy(() -> factory.create(settings(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
    }

    private static ClickHouseLogsSettings settings(URI url) {
        return new ClickHouseLogsSettings(url, Duration.ofSeconds(2), "otel", "otel_logs", "default", null);
    }
}
