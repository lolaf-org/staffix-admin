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

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.alerting.Alert;
import org.lolaf.staffix.api.alerting.ProbeDefinition;
import org.lolaf.staffix.api.alerting.ProbeState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A node's probes of component {@code ha}, as staffix names them. Polled conditions: none can be cleared by hand.
 */
final class SimulatedHaProbes {

    static final String FOLLOWERS = "ha-followers";
    static final String LEADER_LINK = "ha-leader-link";
    static final String LEADERSHIP = "ha-leadership";

    private final List<SimulatedProbes.Probe> probes = List.of(
            probe(FOLLOWERS, "While leading: the followers connected, in sync and applying the writes, without which a failover loses the sessions' latest writes."),
            probe(LEADER_LINK, "While following: the connection to the leader and whether this node holds every replicated session in full, as a failover to it needs."),
            probe(LEADERSHIP, "Whether this node knows its role: without one it neither runs the sessions nor replicates them."));

    private static SimulatedProbes.Probe probe(String name, String description) {
        return new SimulatedProbes.Probe(ProbeDefinition.builder()
                .name(name)
                .description(description)
                .evaluationInterval(Duration.ofSeconds(1))
                .onlyWhileActive(false)
                .build());
    }

    List<ProbeDefinition> definitions() {
        return probes.stream().map(probe -> probe.definition).toList();
    }

    List<ProbeState> states() {
        return probes.stream().map(SimulatedProbes.Probe::state).filter(state -> state != null).toList();
    }

    /**
     * Returns the states whose level changed.
     */
    List<ProbeState> evaluate(Function<String, @Nullable Alert> alerts, Instant now) {
        List<ProbeState> transitions = new ArrayList<>();
        for (SimulatedProbes.Probe probe : probes) {
            if (probe.evaluate(alerts.apply(probe.definition.getName()), now)) {
                transitions.add(probe.state());
            }
        }
        return transitions;
    }
}
