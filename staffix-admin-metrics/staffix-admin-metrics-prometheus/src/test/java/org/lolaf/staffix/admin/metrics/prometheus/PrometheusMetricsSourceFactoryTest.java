package org.lolaf.staffix.admin.metrics.prometheus;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrometheusMetricsSourceFactoryTest {

    private final PrometheusMetricsSourceFactory factory = new PrometheusMetricsSourceFactory(RestClient.builder(), JsonMapper.builder().build());

    @Test
    void buildsASourcePerConnector() {
        assertThat(factory.create(settings(URI.create("http://alpha:9090"))))
                .isNotSameAs(factory.create(settings(URI.create("http://beta:9090"))));
    }

    @Test
    void requiresTheUrl() {
        assertThatThrownBy(() -> factory.create(settings(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
    }

    private static PrometheusSettings settings(URI url) {
        return new PrometheusSettings(url, Duration.ofSeconds(2), TimeUnit.MILLISECONDS, Duration.ofMinutes(1));
    }
}
