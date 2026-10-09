/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.server.engines;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.server.SteppingClock;
import org.lolaf.staffix.admin.server.onboarding.EngineAnnouncement;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EngineRegistryTest {

    private static final URI ALPHA = URI.create("http://alpha:8686/engines/alpha-engine");
    private static final URI ELSEWHERE = URI.create("http://elsewhere:8686/engines/alpha-engine");

    private final EngineProbe probe = mock(EngineProbe.class);
    private final SteppingClock clock = new SteppingClock();

    private EngineRegistry registry(boolean requireEngineTls) {
        return new EngineRegistry(new AdminProperties(Duration.ofSeconds(90), Duration.ofSeconds(5), Path.of("data"), null,
                requireEngineTls), probe, clock);
    }

    private static EngineAnnouncement announcement(URI baseUrl, String token) {
        return EngineAnnouncement.builder().engineId("alpha-engine").baseUrl(baseUrl).token(token).build();
    }

    private static EngineAnnouncement announcement(String baseUrl) {
        return announcement(URI.create(baseUrl), "alpha-token");
    }

    private void answers(URI baseUrl, String token, boolean answers) {
        when(probe.answersAs(eq(baseUrl), eq(token), eq("alpha-engine"))).thenReturn(answers);
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

    @Test
    void anUnchangedAnnouncementIsNotProbed() {
        EngineRegistry registry = registry(false);

        registry.announce(announcement(ALPHA, "alpha-token"));
        registry.announce(announcement(ALPHA, "alpha-token"));

        verifyNoInteractions(probe);
    }

    @Test
    void anotherUrlIsRefusedWhileTheEngineStillAnswersAtItsOwn() {
        EngineRegistry registry = registry(false);
        registry.announce(announcement(ALPHA, "alpha-token"));
        answers(ALPHA, "alpha-token", true);
        answers(ELSEWHERE, "other-token", true);

        assertThatThrownBy(() -> registry.announce(announcement(ELSEWHERE, "other-token")))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(registry.get("alpha-engine").getBaseUrl()).isEqualTo(ALPHA);
    }

    @Test
    void anEngineThatMovedIsAcceptedOnceItsOldUrlNoLongerAnswers() {
        EngineRegistry registry = registry(false);
        registry.announce(announcement(ALPHA, "alpha-token"));
        answers(ALPHA, "alpha-token", false);
        answers(ELSEWHERE, "new-token", true);

        registry.announce(announcement(ELSEWHERE, "new-token"));

        assertThat(registry.get("alpha-engine").getBaseUrl()).isEqualTo(ELSEWHERE);
    }

    @Test
    void aStaleEngineIsNotProbedAtItsOldUrl() {
        EngineRegistry registry = registry(false);
        registry.announce(announcement(ALPHA, "alpha-token"));
        clock.step(Duration.ofSeconds(91));
        answers(ELSEWHERE, "new-token", true);

        registry.announce(announcement(ELSEWHERE, "new-token"));

        assertThat(registry.get("alpha-engine").getBaseUrl()).isEqualTo(ELSEWHERE);
    }

    @Test
    void aRestartedEngineGetsItsNewTokenInWhenItAnswersWithIt() {
        EngineRegistry registry = registry(false);
        registry.announce(announcement(ALPHA, "alpha-token"));
        answers(ALPHA, "new-token", true);

        registry.announce(announcement(ALPHA, "new-token"));

        assertThat(registry.get("alpha-engine").getToken()).isEqualTo("new-token");
    }

    @Test
    void aTokenTheEngineRefusesIsNotRegistered() {
        EngineRegistry registry = registry(false);
        registry.announce(announcement(ALPHA, "alpha-token"));
        when(probe.answersAs(any(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> registry.announce(announcement(ALPHA, "guessed-token")))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(registry.get("alpha-engine").getToken()).isEqualTo("alpha-token");
    }
}
