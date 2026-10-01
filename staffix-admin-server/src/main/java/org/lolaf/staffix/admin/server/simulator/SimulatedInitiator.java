package org.lolaf.staffix.admin.server.simulator;

import org.lolaf.staffix.admin.server.sessions.InitiatorTarget;
import org.lolaf.staffix.admin.server.sessions.InitiatorTargets;

import java.util.List;
import java.util.Optional;

/**
 * Like a staffix initiator, only the active target is a managed session; each target keeps its own sequence numbers.
 */
class SimulatedInitiator {

    final String initiatorId;
    private final List<SimulatedSession> targets;
    private SimulatedSession active;

    SimulatedInitiator(String initiatorId, List<SimulatedSession> targets) {
        this.initiatorId = initiatorId;
        this.targets = List.copyOf(targets);
        this.active = targets.getFirst();
    }

    SimulatedSession active() {
        return active;
    }

    Optional<SimulatedSession> target(String sessionId) {
        return targets.stream().filter(t -> t.id.equals(sessionId)).findFirst();
    }

    boolean hasTarget(String sessionId) {
        return target(sessionId).isPresent();
    }

    void switchTo(SimulatedSession target) {
        if (target == active) {
            return;
        }
        active.loggedIn = false;
        active = target;
        target.drop(2);
    }

    InitiatorTargets describe() {
        return InitiatorTargets.builder()
                .initiatorId(initiatorId)
                .activeSessionId(active.id)
                .targets(targets.stream()
                        .map(t -> InitiatorTarget.builder()
                                .sessionId(t.id)
                                .connectAddress(t.connectAddress)
                                .build())
                        .toList())
                .build();
    }
}
