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
package org.lolaf.staffix.admin.simulator;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.api.alerting.ProbeState;
import org.lolaf.staffix.api.session.FixSessionStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedProbesTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    void aLargeOrderAlertClearsByHandUntilItsNextCycleAndOnlyItsTransitionSaysSo() {
        SimulatedProbes probes = new SimulatedProbes(2);
        assertThat(probes.tick(0, FixSessionStatus.LOGGED_IN, NOW)).extracting(ProbeState::getProbeName).containsExactly("large-order");

        assertThat(probes.clear("session-down")).isFalse();
        assertThat(probes.clear("large-order")).isTrue();

        assertThat(probes.tick(1, FixSessionStatus.LOGGED_IN, NOW)).singleElement().satisfies(cleared -> {
            assertThat(cleared.getAlert()).isEmpty();
            assertThat(cleared.isClearedManually()).isTrue();
        });
        assertThat(probes.tick(2, FixSessionStatus.LOGGED_IN, NOW)).isEmpty();
        assertThat(probes.states()).noneMatch(ProbeState::isClearedManually);
        assertThat(probes.clear("large-order")).isFalse();
    }
}
