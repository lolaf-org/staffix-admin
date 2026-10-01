package org.lolaf.staffix.admin.server.simulator;

import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import tools.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An in-memory engine following staffix's AdminApi rules, so the UI meets the same refusals it will meet for real.
 */
class SimulatedEngine {

    final String instanceId;
    final String staffixVersion;
    private final List<SimulatedSession> acceptorSessions;
    private final List<SimulatedInitiator> initiators;

    SimulatedEngine(String instanceId, String staffixVersion, List<SimulatedSession> acceptorSessions,
                    List<SimulatedInitiator> initiators) {
        this.instanceId = instanceId;
        this.staffixVersion = staffixVersion;
        this.acceptorSessions = List.copyOf(acceptorSessions);
        this.initiators = List.copyOf(initiators);
    }

    synchronized void tick(Random random) {
        managedSessions().forEach(s -> s.tick(random));
    }

    synchronized List<SessionGroup> listSessions() {
        TreeMap<String, List<SessionSummary>> byGroup = managedSessions()
                .map(SimulatedSession::summary)
                .sorted(Comparator.comparing(SessionSummary::getId))
                .collect(Collectors.groupingBy(SessionSummary::getGroup, TreeMap::new, Collectors.toList()));
        return byGroup.entrySet().stream()
                .map(e -> SessionGroup.builder().group(e.getKey()).sessions(e.getValue()).build())
                .toList();
    }

    synchronized SessionDetail getSession(String sessionId) {
        SimulatedSession session = managed(sessionId);
        return SessionDetail.builder()
                .summary(session.summary())
                .incomingSeqNum(session.incomingSeqNum)
                .outgoingSeqNum(session.outgoingSeqNum)
                .initiators(initiators.stream()
                        .filter(i -> i.hasTarget(sessionId))
                        .map(SimulatedInitiator::describe)
                        .toList())
                .build();
    }

    synchronized JsonNode getSessionSettings(String sessionId) {
        return managed(sessionId).settings;
    }

    synchronized void logon(String sessionId) {
        managed(sessionId).logon();
    }

    synchronized void logout(String sessionId) {
        managed(sessionId).logout();
    }

    synchronized void reset(String sessionId, ResetMode mode) {
        SimulatedSession session = managed(sessionId);
        switch (mode) {
            case LOGOUT_LOGON_REST_NUM_FLAG -> {
                if (session.type != SessionType.INITIATOR) {
                    throw GatewayException.rejected(mode + " needs an initiator session: only the initiator sends the Logon carrying the reset");
                }
                session.resetSeqNums(1);
                session.drop(2);
            }
            case RESET_SEQUENCE -> session.resetSeqNums(1);
            case RESET_SEQUENCE_IN_SESSION -> {
                requireLoggedIn(session, mode.name());
                session.resetSeqNums(2);
            }
        }
    }

    synchronized void activateInitiatorTarget(String sessionId) {
        List<SimulatedInitiator> owners = initiators.stream().filter(i -> i.hasTarget(sessionId)).toList();
        if (owners.isEmpty()) {
            throw GatewayException.unknownSession(instanceId, sessionId);
        }
        owners.forEach(i -> i.switchTo(i.target(sessionId).orElseThrow()));
    }

    synchronized void setSeqNums(String sessionId, Long incoming, Long outgoing) {
        SimulatedSession session = managed(sessionId);
        if (incoming != null) {
            session.incomingSeqNum = incoming;
        }
        if (outgoing != null) {
            session.outgoingSeqNum = outgoing;
        }
    }

    synchronized void sendMessage(String sessionId, String message, char separator, boolean possDup) {
        SimulatedSession session = managed(sessionId);
        requireLoggedIn(session, "Sending");
        List<String> tags = Stream.of(message.split(Pattern.quote(String.valueOf(separator))))
                .map(field -> field.contains("=") ? field.substring(0, field.indexOf('=')) : "")
                .toList();
        if (!tags.contains("35")) {
            throw GatewayException.invalid("The message has no MsgType(35) field, separated by '" + separator + "'");
        }
        if (possDup && !tags.contains("52")) {
            throw GatewayException.invalid("A PossDup message needs a SendingTime(52) to become its OrigSendingTime(122)");
        }
        session.outgoingSeqNum++;
    }

    private void requireLoggedIn(SimulatedSession session, String operation) {
        if (!session.loggedIn) {
            throw GatewayException.rejected(operation + " needs session '" + session.id + "' to be logged in");
        }
    }

    private SimulatedSession managed(String sessionId) {
        return managedSessions()
                .filter(s -> s.id.equals(sessionId))
                .findFirst()
                .orElseThrow(() -> GatewayException.unknownSession(instanceId, sessionId));
    }

    private Stream<SimulatedSession> managedSessions() {
        return Stream.concat(acceptorSessions.stream(), initiators.stream().map(SimulatedInitiator::active))
                .collect(Collectors.toMap(s -> s.id, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream();
    }
}
