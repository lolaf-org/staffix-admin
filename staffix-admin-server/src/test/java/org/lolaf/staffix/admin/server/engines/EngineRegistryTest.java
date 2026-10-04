package org.lolaf.staffix.admin.server.engines;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.server.onboarding.EngineAnnouncement;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineRegistryTest {

    private static EngineRegistry registry(boolean requireEngineTls) {
        return new EngineRegistry(new AdminProperties(Duration.ofSeconds(90), Duration.ofSeconds(5), Path.of("data"), null,
                requireEngineTls), Clock.systemUTC());
    }

    private static EngineAnnouncement announcement(String baseUrl) {
        return EngineAnnouncement.builder().engineId("alpha-engine").baseUrl(URI.create(baseUrl)).token("alpha-token").build();
    }

    @Test
    void aPlainHttpEngineIsAcceptedUnlessTlsIsRequired() {
        EngineRegistry registry = registry(false);

        registry.announce(announcement("http://alpha:8686/engines/alpha-engine"));

        assertThat(registry.get("alpha-engine").getBaseUrl()).hasScheme("http");
    }

    @Test
    void requiringTlsRefusesAPlainHttpEngine() {
        EngineRegistry registry = registry(true);

        assertThatThrownBy(() -> registry.announce(announcement("http://alpha:8686/engines/alpha-engine")))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(registry.list()).isEmpty();
    }

    @Test
    void requiringTlsAcceptsAnHttpsEngine() {
        EngineRegistry registry = registry(true);

        registry.announce(announcement("https://alpha:8686/engines/alpha-engine"));

        assertThat(registry.get("alpha-engine").getBaseUrl()).hasScheme("https");
    }
}
