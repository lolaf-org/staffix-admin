package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.sessions.SessionState;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Three engines covering every case the UI must render: all three colours, an operator logout, flaky sessions,
 * session windows opening and closing, initiators with and without backup targets.
 */
@RequiredArgsConstructor
class SimulationScenario {

    private final JsonMapper jsonMapper;

    List<SimulatedEngine> engines() {
        return List.of(paris(), london(), newYork());
    }

    private SimulatedEngine paris() {
        EngineBuilder engine = new EngineBuilder("paris-prod", "0.9.1", "STAFFIX-PAR", "Europe/Paris");
        IntStream.rangeClosed(1, 10).forEach(i -> engine.acceptor("equities", "EQ-CLIENT-%02d".formatted(i))
                .flaky(i == 3)
                .withinSessionTime(i != 7)
                .desiredState(i == 9 ? SessionState.LOGGED_OUT : SessionState.LOGGED_IN));
        engine.initiator("equities", "EQ-BROKER", "EQ-BROKER-DR");
        engine.initiator("fx", "FX-LP1").get(0).flaky(true);
        engine.initiator("fx", "FX-LP2", "FX-LP2-DR");
        IntStream.rangeClosed(1, 4).forEach(i -> engine.acceptor("fx", "FX-CLIENT-%02d".formatted(i))
                .sessionTimeCycleTicks(i == 2 ? 30 : 0));
        engine.acceptor("default", "DROPCOPY");
        return engine.build();
    }

    private SimulatedEngine london() {
        EngineBuilder engine = new EngineBuilder("london-prod", "0.9.1", "STAFFIX-LDN", "Europe/London");
        engine.initiator("derivatives", "DRV-EUREX").get(0).sessionTimeCycleTicks(45);
        engine.initiator("derivatives", "DRV-ICE", "DRV-ICE-DR");
        engine.initiator("derivatives", "DRV-CME").get(0).flaky(true);
        engine.initiator("equities", "EQ-LSE");
        IntStream.rangeClosed(21, 26).forEach(i -> engine.acceptor("equities", "EQ-CLIENT-%02d".formatted(i)));
        return engine.build();
    }

    private SimulatedEngine newYork() {
        EngineBuilder engine = new EngineBuilder("nyc-uat", "0.9.0", "STAFFIX-NYC", "America/New_York");
        IntStream.rangeClosed(1, 5).forEach(i -> engine.acceptor("default", "UAT-CLIENT-%02d".formatted(i))
                .flaky(i == 2)
                .desiredState(i == 3 ? SessionState.LOGGED_OUT : SessionState.LOGGED_IN));
        return engine.build();
    }

    private class EngineBuilder {

        private final String instanceId;
        private final String staffixVersion;
        private final String compId;
        private final String timeZone;
        private final List<SimulatedSession.SimulatedSessionBuilder> acceptors = new ArrayList<>();
        private final List<List<SimulatedSession.SimulatedSessionBuilder>> initiators = new ArrayList<>();
        private int nextPort = 9001;

        EngineBuilder(String instanceId, String staffixVersion, String compId, String timeZone) {
            this.instanceId = instanceId;
            this.staffixVersion = staffixVersion;
            this.compId = compId;
            this.timeZone = timeZone;
        }

        SimulatedSession.SimulatedSessionBuilder acceptor(String group, String id) {
            SimulatedSession.SimulatedSessionBuilder session = session(group, id, SessionType.ACCEPTOR, null);
            acceptors.add(session);
            return session;
        }

        /**
         * The first target is the main one, the others its backups.
         */
        List<SimulatedSession.SimulatedSessionBuilder> initiator(String group, String... targetIds) {
            List<SimulatedSession.SimulatedSessionBuilder> targets = new ArrayList<>();
            for (String id : targetIds) {
                String host = id.toLowerCase().replace("-dr", "") + (id.endsWith("-DR") ? ".dr" : "") + ".example.com";
                targets.add(session(group, id, SessionType.INITIATOR, host + ":" + nextPort++));
            }
            initiators.add(targets);
            return targets;
        }

        SimulatedEngine build() {
            return new SimulatedEngine(instanceId, staffixVersion,
                    acceptors.stream().map(SimulatedSession.SimulatedSessionBuilder::build).toList(),
                    initiators.stream().map(EngineBuilder::initiator).toList());
        }

        private static SimulatedInitiator initiator(List<SimulatedSession.SimulatedSessionBuilder> targetBuilders) {
            List<SimulatedSession> targets = targetBuilders.stream().map(SimulatedSession.SimulatedSessionBuilder::build).toList();
            return new SimulatedInitiator(targets.getFirst().id + "-initiator", targets);
        }

        private SimulatedSession.SimulatedSessionBuilder session(String group, String id, SessionType type,
                                                                 String connectAddress) {
            String counterparty = id.replace("-DR", "");
            String fixSessionId = id + ":FIX.4.4:" + compId + "->" + counterparty;
            return SimulatedSession.builder()
                    .id(id)
                    .group(group)
                    .fixSessionId(fixSessionId)
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
}
