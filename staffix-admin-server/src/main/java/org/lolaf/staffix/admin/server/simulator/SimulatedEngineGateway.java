package org.lolaf.staffix.admin.server.simulator;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

class SimulatedEngineGateway implements EngineGateway {

    private final Map<String, SimulatedEngine> engines;

    SimulatedEngineGateway(List<SimulatedEngine> engines) {
        this.engines = engines.stream().collect(Collectors.toUnmodifiableMap(e -> e.instanceId, Function.identity()));
    }

    Collection<SimulatedEngine> engines() {
        return engines.values();
    }

    void tick(Random random) {
        engines.values().forEach(e -> e.tick(random));
    }

    @Override
    public List<SessionGroup> listSessions(String instanceId) {
        return engine(instanceId).listSessions();
    }

    @Override
    public SessionDetail getSession(String instanceId, String sessionId) {
        return engine(instanceId).getSession(sessionId);
    }

    @Override
    public JsonNode getSessionSettings(String instanceId, String sessionId) {
        return engine(instanceId).getSessionSettings(sessionId);
    }

    @Override
    public void logon(String instanceId, String sessionId) {
        engine(instanceId).logon(sessionId);
    }

    @Override
    public void logout(String instanceId, String sessionId) {
        engine(instanceId).logout(sessionId);
    }

    @Override
    public void reset(String instanceId, String sessionId, ResetMode mode) {
        engine(instanceId).reset(sessionId, mode);
    }

    @Override
    public void activateInitiatorTarget(String instanceId, String sessionId) {
        engine(instanceId).activateInitiatorTarget(sessionId);
    }

    @Override
    public void setSeqNums(String instanceId, String sessionId, @Nullable Long incoming, @Nullable Long outgoing) {
        engine(instanceId).setSeqNums(sessionId, incoming, outgoing);
    }

    @Override
    public void sendMessage(String instanceId, String sessionId, String message, char separator, boolean possDup) {
        engine(instanceId).sendMessage(sessionId, message, separator, possDup);
    }

    private SimulatedEngine engine(String instanceId) {
        SimulatedEngine engine = engines.get(instanceId);
        if (engine == null) {
            throw GatewayException.unknownEngine(instanceId);
        }
        return engine;
    }
}
