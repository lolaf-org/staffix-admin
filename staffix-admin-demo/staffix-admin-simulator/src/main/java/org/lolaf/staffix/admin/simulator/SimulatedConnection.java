package org.lolaf.staffix.admin.simulator;

import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;

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
