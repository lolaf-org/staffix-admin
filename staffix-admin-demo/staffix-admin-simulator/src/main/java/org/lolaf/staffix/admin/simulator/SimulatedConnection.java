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

import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;

import java.util.List;

/**
 * What an initiator or an acceptor manages for one session: its configs (an initiator's main first, then its backups;
 * an acceptor's only one) of which one runs.
 */
final class SimulatedConnection {

    final String instanceId;
    final FixSessionType type;
    final List<SimulatedSession> configs;
    private SimulatedSession running;

    SimulatedConnection(String instanceId, FixSessionType type, List<SimulatedSession> configs) {
        this.instanceId = instanceId;
        this.type = type;
        this.configs = List.copyOf(configs);
        this.running = configs.getFirst();
    }

    SimulatedSession running() {
        return running;
    }

    SimulatedSession main() {
        return configs.getFirst();
    }

    List<SimulatedSession> backups() {
        return configs.subList(1, configs.size());
    }

    /**
     * A backup runs on its main config's settings under its own id.
     */
    void restartOn(FixSessionSettings settings) {
        main().restartOn(settings);
        backups().forEach(backup -> backup.restartOn(settings.toBuilder().fixSessionId(backup.fixSessionId()).build()));
    }

    boolean hasConfig(FixSessionId fixSessionId) {
        return configs.stream().anyMatch(config -> config.fixSessionId().equals(fixSessionId));
    }

    void switchTo(FixSessionId fixSessionId) {
        SimulatedSession target = configs.stream()
                .filter(config -> config.fixSessionId().equals(fixSessionId))
                .findFirst()
                .orElseThrow();
        if (target != running) {
            running.loggedIn = false;
            running = target;
            target.drop(2);
        }
    }
}
