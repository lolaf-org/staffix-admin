package org.lolaf.staffix.admin.server.simulator;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.lolaf.staffix.admin.server.sessions.ApplicationSetting;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.metrics.SessionMeters;
import org.lolaf.staffix.admin.server.sessions.SessionComponents;
import org.lolaf.staffix.admin.server.sessions.SessionSettingsStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class SimulatedEngineGateway implements EngineGateway {

    private static final String MONITORING_PLUGIN_TYPE = "org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager";

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

    /**
     * What the simulated metrics and logs cover: the running configs of the scope. A config that exists but does not
     * run yields nothing, as a real backend holds nothing for it.
     */
    List<SimulatedSession> runningConfigs(String engineId, @Nullable String group, @Nullable String configId) {
        SimulatedEngine engine = engine(engineId);
        if (configId != null && !engine.hasConfig(group, configId)) {
            throw GatewayException.unknownSession(SessionRef.of(engineId, group, configId));
        }
        return engine.runningConfigs().stream()
                .filter(s -> group == null || s.group.equals(group))
                .filter(s -> configId == null || s.id.equals(configId))
                .toList();
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
    public void updateSessionSettings(SessionRef session, JsonNode settings) {
        engine(session.getEngineId()).updateSessionSettings(session, settings);
    }

    @Override
    public void removeSession(SessionRef session) {
        engine(session.getEngineId()).removeSession(session);
    }

    @Override
    public void addSession(String engineId, String sessionSettingsStore, JsonNode settings) {
        engine(engineId);
        throw GatewayException.rejected("The simulator does not add sessions");
    }

    @Override
    public JsonNode getSessionSettingsSchema(String engineId) {
        engine(engineId);
        return JsonNodeFactory.instance.objectNode().put("type", "object");
    }

    @Override
    public List<SessionSettingsStore> listSessionSettingsStores(String engineId) {
        engine(engineId);
        return List.of(SessionSettingsStore.builder().instanceId(SimulatedEngine.SETTINGS_STORE).build());
    }

    @Override
    public List<SessionMeters> getMeters(String engineId) {
        return engine(engineId).meters();
    }

    @Override
    public SessionComponents getSessionComponents(String engineId) {
        JsonNode emitters = getEmitters(engineId);
        SessionComponents.SessionComponentsBuilder components = SessionComponents.builder()
                .applicationFactory(SessionComponents.ApplicationFactory.builder().instanceId("default").applicationId("default").build())
                .messagesStore("default");
        emitters.path("messagesLoggers").forEach(logger -> components.messagesLogger(logger.path("instanceId").asString()));
        emitters.path("sessionsPlugins").forEach(plugin -> components.sessionsPlugin(SessionComponents.SessionsPlugin.builder()
                .instanceId(plugin.path("instanceId").asString())
                .pluginType(MONITORING_PLUGIN_TYPE)
                .build()));
        return components.build();
    }

    @Override
    public List<ApplicationSetting> getApplicationSettings(SessionRef session) {
        engine(session.getEngineId()).getSession(session);
        return List.of();
    }

    @Override
    public JsonNode getEmitters(String engineId) {
        return engine(engineId).getEmitters();
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
    public void sendMessages(SessionRef session, List<String> messages, char separator, boolean possDup) {
        for (int i = 0; i < messages.size(); i++) {
            try {
                engine(session.getEngineId()).sendMessage(session, messages.get(i), separator, possDup);
            } catch (GatewayException e) {
                if (messages.size() == 1) {
                    throw e;
                }
                String detail = "Message " + (i + 1) + " of " + messages.size() + ": " + e.getBody().getDetail();
                throw e.getStatusCode().value() == 400 ? GatewayException.invalid(detail) : GatewayException.rejected(detail);
            }
        }
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
