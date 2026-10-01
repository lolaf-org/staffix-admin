package org.lolaf.staffix.admin.server.simulator;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
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
    private final SimulatedDictionary dictionary = new SimulatedDictionary();

    SimulatedEngineGateway(List<SimulatedEngine> engines) {
        this.engines = engines.stream().collect(Collectors.toUnmodifiableMap(e -> e.engineId, Function.identity()));
    }

    Collection<SimulatedEngine> engines() {
        return engines.values();
    }

    SimulatedEngine engine(String engineId) {
        SimulatedEngine engine = engines.get(engineId);
        if (engine == null) {
            throw GatewayException.unknownEngine(engineId);
        }
        return engine;
    }

    void tick(Random random) {
        engines.values().forEach(e -> e.tick(random));
    }

    @Override
    public List<SessionGroup> listGroups(String engineId) {
        return engine(engineId).listGroups();
    }

    @Override
    public SessionDetail getSession(SessionRef session) {
        return engine(session.getEngineId()).getSession(session);
    }

    @Override
    public JsonNode getSessionSettings(SessionRef session) {
        return engine(session.getEngineId()).getSessionSettings(session);
    }

    @Override
    public void logon(SessionRef session) {
        engine(session.getEngineId()).logon(session);
    }

    @Override
    public void logout(SessionRef session) {
        engine(session.getEngineId()).logout(session);
    }

    @Override
    public void reset(SessionRef session, ResetMode mode) {
        engine(session.getEngineId()).reset(session, mode);
    }

    @Override
    public void activate(SessionRef session, String configId) {
        engine(session.getEngineId()).activate(session, configId);
    }

    @Override
    public void setSeqNums(SessionRef session, @Nullable Long incoming, @Nullable Long outgoing) {
        engine(session.getEngineId()).setSeqNums(session, incoming, outgoing);
    }

    @Override
    public void sendMessage(SessionRef session, String message, char separator, boolean possDup) {
        engine(session.getEngineId()).sendMessage(session, message, separator, possDup);
    }

    @Override
    public List<DictionaryRef> getDictionaries(SessionRef config) {
        if (!engine(config.getEngineId()).hasConfig(config.getGroup(), config.getSessionId())) {
            throw GatewayException.unknownSession(config);
        }
        return List.of(dictionary.ref());
    }

    @Override
    public byte[] getDictionaryXml(String engineId, String dictionaryId) {
        engine(engineId);
        if (!dictionaryId.equals(dictionary.ref().getId())) {
            throw GatewayException.unknownDictionary(engineId, dictionaryId);
        }
        return dictionary.xml();
    }
}
