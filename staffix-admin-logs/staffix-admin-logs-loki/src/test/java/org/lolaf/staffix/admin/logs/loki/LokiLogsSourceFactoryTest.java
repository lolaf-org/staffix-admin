package org.lolaf.staffix.admin.logs.loki;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LokiLogsSourceFactoryTest {

    private final LokiLogsSourceFactory factory = new LokiLogsSourceFactory(RestClient.builder());

    @Test
    void buildsASourcePerConnector() {
        assertThat(factory.create(settings(URI.create("http://alpha:3100"))))
                .isNotSameAs(factory.create(settings(URI.create("http://beta:3100"))));
    }

    @Test
    void requiresTheUrl() {
        assertThatThrownBy(() -> factory.create(settings(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
    }

    private static LokiSettings settings(URI url) {
        return new LokiSettings(url, Duration.ofSeconds(2), "{service_name=~\".+\"}", Set.of(), "alpha-tenant");
    }
}
