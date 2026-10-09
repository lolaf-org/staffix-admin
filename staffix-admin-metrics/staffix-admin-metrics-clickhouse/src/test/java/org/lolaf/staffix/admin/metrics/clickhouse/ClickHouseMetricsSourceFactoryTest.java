package org.lolaf.staffix.admin.metrics.clickhouse;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseMetricsSourceFactoryTest {

    private final ClickHouseMetricsSourceFactory factory = new ClickHouseMetricsSourceFactory(RestClient.builder(), JsonMapper.builder().build());

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

    private static ClickHouseMetricsSettings settings(URI url) {
        return new ClickHouseMetricsSettings(url, Duration.ofSeconds(2), "otel", "otel_metrics_histogram", "otel_metrics_gauge",
                "default", null, TimeUnit.MILLISECONDS, Duration.ofSeconds(10));
    }
}
