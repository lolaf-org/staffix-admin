package org.lolaf.staffix.admin.server.simulator;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.SessionConfig;
import org.lolaf.staffix.admin.server.sessions.SessionHealth;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.sessions.SessionType;

import java.util.List;

/**
 * One box of the overview: a session bound to an initiator, which runs one of its configs (main or a backup), or to
 * an acceptor, which has a single config.
 */
class SimulatedConnection {

    final String group;
    final String id;
    final SessionType type;
    final String instanceId;
    private final Emitters emitters;
    private final List<SimulatedSession> configs;
    private SimulatedSession running;

    SimulatedConnection(String instanceId, Emitters emitters, List<SimulatedSession> configs) {
        SimulatedSession main = configs.getFirst();
        this.group = main.group;
        this.id = main.id;
        this.type = main.type;
        this.instanceId = instanceId;
        this.emitters = emitters;
        this.configs = List.copyOf(configs);
        this.running = main;
    }

    Emitters emitters() {
        return emitters;
    }

    SimulatedSession running() {
        return running;
    }

    void activate(String configId) {
        SimulatedSession target = configs.stream()
                .filter(c -> c.id.equals(configId))
                .findFirst()
                .orElseThrow(() -> GatewayException.invalid("Session '" + group + "." + id + "' has no config '" + configId + "'"));
        if (target == running) {
            return;
        }
        running.loggedIn = false;
        running = target;
        target.drop(2);
    }

    boolean hasConfig(String configId) {
        return configs.stream().anyMatch(c -> c.id.equals(configId));
    }

    SessionSummary summary() {
        return SessionSummary.builder()
                .group(group)
                .id(id)
                .type(type)
                .instanceId(instanceId)
                .configIds(configs.stream().map(c -> c.id).toList())
                .runningConfigId(running.id)
                .identity(running.identity)
                .health(SessionHealth.of(running.loggedIn, running.withinSessionTime))
                .loggedIn(running.loggedIn)
                .connected(running.loggedIn)
                .withinSessionTime(running.withinSessionTime)
                .desiredState(running.desiredState)
                .messagesLoggerInstanceId(emitters.messagesLogger())
                .monitoringInstanceId(emitters.monitoring())
                .build();
    }

    List<SessionConfig> describeConfigs() {
        return configs.stream()
                .map(c -> SessionConfig.builder()
                        .id(c.id)
                        .connectAddresses(c.connectAddress == null ? List.of() : List.of(c.connectAddress))
                        .build())
                .toList();
    }

    record Emitters(String messagesLogger, @Nullable String monitoring) {
    }
}
