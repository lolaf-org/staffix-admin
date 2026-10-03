package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.sessions.CompIds;
import org.lolaf.staffix.admin.server.sessions.FixIdentity;
import org.lolaf.staffix.admin.server.sessions.SessionState;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Three engines whose groups are counterparties: liquidity providers we connect to (initiators, with backup
 * configs), clients connecting to us (acceptors), one group mixing both. Between them they show every colour, an
 * operator logout, flaky sessions, session windows opening and closing, and a backup running.
 */
@RequiredArgsConstructor
class SimulationScenario {

    private final JsonMapper jsonMapper;

    List<SimulatedEngine> engines() {
        return List.of(paris(), london(), newYork());
    }

    private SimulatedEngine paris() {
        EngineBuilder engine = new EngineBuilder("paris-prod", "0.9.1", "STAFFIX-PAR", "Europe/Paris");
        engine.initiator("ubs", "trading", "trading-drp");
        engine.initiator("ubs", "market-data", "market-data-drp").getFirst().flaky(true);
        engine.initiator("ubs", "drop-copy");
        engine.initiator("jpm", "trading", "trading-drp");
        engine.initiator("jpm", "market-data").getFirst().sessionTimeCycleTicks(45);
        engine.initiator("citi", "trading", "trading-drp");
        engine.emitters("clients-otlp", "clients-metrics");
        engine.acceptor("citi", "drop-copy", "dropcopy-acceptor");
        engine.acceptor("acme", "trading", "clients-acceptor");
        engine.acceptor("acme", "drop-copy", "clients-acceptor");
        engine.acceptor("globex", "trading", "clients-acceptor").desiredState(SessionState.LOGGED_OUT);
        engine.acceptor("globex", "drop-copy", "clients-acceptor").withinSessionTime(false);
        engine.acceptor("initech", "trading", "clients-acceptor").flaky(true);
        engine.acceptor("initech", "drop-copy", "clients-acceptor");
        return engine.build();
    }

    private SimulatedEngine london() {
        EngineBuilder engine = new EngineBuilder("london-prod", "0.9.1", "STAFFIX-LDN", "Europe/London");
        engine.initiator("barclays", "trading", "trading-drp");
        engine.initiator("barclays", "market-data");
        engine.initiator("ice", "trading", "trading-drp").getFirst().flaky(true);
        engine.acceptor("fund-alpha", "trading", "clients-acceptor");
        engine.acceptor("fund-alpha", "drop-copy", "clients-acceptor");
        engine.acceptor("fund-beta", "trading", "clients-acceptor").sessionTimeCycleTicks(30);
        return engine.build();
    }

    private SimulatedEngine newYork() {
        EngineBuilder engine = new EngineBuilder("nyc-uat", "0.9.0", "STAFFIX-NYC", "America/New_York");
        engine.emitters("slf4j", null);
        engine.acceptor("uat-client-1", "trading", "uat-acceptor");
        engine.acceptor("uat-client-2", "trading", "uat-acceptor").flaky(true);
        engine.acceptor("uat-client-3", "trading", "uat-acceptor").desiredState(SessionState.LOGGED_OUT);
        return engine.build();
    }

    private class EngineBuilder {

        private final String engineId;
        private final String staffixVersion;
        private final String compId;
        private final String timeZone;
        private final List<Connection> connections = new ArrayList<>();
        private SimulatedConnection.Emitters emitters = new SimulatedConnection.Emitters("otlp", "otlp-metrics");
        private int nextPort = 9001;

        EngineBuilder(String engineId, String staffixVersion, String compId, String timeZone) {
            this.engineId = engineId;
            this.staffixVersion = staffixVersion;
            this.compId = compId;
            this.timeZone = timeZone;
        }

        /**
         * For the sessions declared next.
         */
        void emitters(String messagesLogger, @Nullable String monitoring) {
            emitters = new SimulatedConnection.Emitters(messagesLogger, monitoring);
        }

        SimulatedSession.SimulatedSessionBuilder acceptor(String group, String id, String instanceId) {
            SimulatedSession.SimulatedSessionBuilder config = config(group, id, SessionType.ACCEPTOR, null);
            connections.add(new Connection(instanceId, emitters, List.of(config)));
            return config;
        }

        /**
         * One initiator per session, named after it; the first config is the main one, the others its backups.
         */
        List<SimulatedSession.SimulatedSessionBuilder> initiator(String group, String... configIds) {
            List<SimulatedSession.SimulatedSessionBuilder> configs = new ArrayList<>();
            for (String configId : configIds) {
                String host = "fix" + (configId.endsWith("-drp") ? "-dr" : "") + "." + group + ".example.com";
                configs.add(config(group, configId, SessionType.INITIATOR, host + ":" + nextPort++));
            }
            connections.add(new Connection(group + "-" + configIds[0], emitters, configs));
            return configs;
        }

        SimulatedEngine build() {
            return new SimulatedEngine(engineId, staffixVersion, connections.stream()
                    .map(c -> new SimulatedConnection(c.instanceId(), c.emitters(), c.configs().stream()
                            .map(SimulatedSession.SimulatedSessionBuilder::build)
                            .toList()))
                    .toList());
        }

        private SimulatedSession.SimulatedSessionBuilder config(String group, String id, SessionType type, String connectAddress) {
            String counterparty = group.toUpperCase();
            String fixSessionId = id + ":FIX.4.4:" + compId + "->" + counterparty;
            return SimulatedSession.builder()
                    .id(id)
                    .group(group)
                    .identity(FixIdentity.builder()
                            .fixVersion("FIX.4.4")
                            .sender(CompIds.builder().compId(compId).build())
                            .target(CompIds.builder().compId(counterparty).build())
                            .build())
                    .type(type)
                    .connectAddress(connectAddress)
                    .withinSessionTime(true)
                    .settings(settings(fixSessionId, group, id, type, counterparty, connectAddress));
        }

        private ObjectNode settings(String fixSessionId, String group, String id, SessionType type,
                                    String counterparty, String connectAddress) {
            ObjectNode settings = jsonMapper.createObjectNode();
            ObjectNode sessionId = settings.putObject("fixSessionId");
            sessionId.put("id", id);
            sessionId.put("group", group);
            sessionId.put("fixVersion", "FIX.4.4");
            sessionId.put("senderCompID", compId);
            sessionId.put("targetCompID", counterparty);
            sessionId.put("toString", fixSessionId);
            settings.put("fixSessionType", type.name());
            settings.put("dictionaryId", "default");
            settings.putObject("heartBeatInterval").put("seconds", 30);
            settings.put("resetSeqNumOnLogon", (Boolean) null);
            settings.put("desiredSessionState", "LOGGED_IN");
            settings.put("logInOrOutResponseTimeout", "PT10S");
            settings.put("resendRequestResponseTimeout", "PT30S");
            settings.put("maxOutOfSequenceMessagesQueued", 10_000);
            settings.put("fixMessageLoggerInstanceId", emitters.messagesLogger());
            ObjectNode plugins = settings.putObject("fixSessionPluginsInstanceIds");
            if (emitters.monitoring() != null) {
                plugins.put("org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager", emitters.monitoring());
            }
            ObjectNode schedule = settings.putObject("sessionScheduleSettings");
            schedule.put("timeZone", timeZone);
            ObjectNode window = schedule.putArray("sessionSchedules").addObject();
            window.put("start", "SUNDAY 22:00");
            window.put("end", "FRIDAY 22:00");
            if (connectAddress != null) {
                settings.putArray("connectAddresses").add(connectAddress);
            }
            return settings;
        }
    }

    private record Connection(String instanceId, SimulatedConnection.Emitters emitters,
                              List<SimulatedSession.SimulatedSessionBuilder> configs) {
    }
}
