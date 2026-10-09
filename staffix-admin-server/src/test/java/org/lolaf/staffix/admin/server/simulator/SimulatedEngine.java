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
package org.lolaf.staffix.admin.server.simulator;

import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.lolaf.staffix.admin.server.metrics.SessionMeters;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An in-memory engine following staffix's AdminApi rules, so the UI meets the same refusals it will meet for real.
 */
class SimulatedEngine {

    static final String SETTINGS_STORE = "simulated";

    final String engineId;
    final String staffixVersion;
    private final List<SimulatedConnection> connections;

    SimulatedEngine(String engineId, String staffixVersion, List<SimulatedConnection> connections) {
        this.engineId = engineId;
        this.staffixVersion = staffixVersion;
        this.connections = new ArrayList<>(connections);
    }

    synchronized List<SessionGroup> listGroups() {
        TreeMap<String, List<SessionSummary>> byGroup = connections.stream()
                .map(SimulatedConnection::summary)
                .sorted(Comparator.comparing(SessionSummary::getId))
                .collect(Collectors.groupingBy(SessionSummary::getGroup, TreeMap::new, Collectors.toList()));
        return byGroup.entrySet().stream()
                .map(e -> SessionGroup.builder().group(e.getKey()).sessions(e.getValue()).build())
                .toList();
    }

    /**
     * Only running configs exchange traffic, so they are what the simulated metrics and logs show.
     */
    synchronized List<SessionMeters> meters() {
        return connections.stream()
                .map(connection -> SessionMeters.builder()
                        .group(connection.running().group)
                        .name(connection.running().id)
                        .meters(SimulatedMeters.of(connection.emitters().monitoring()))
                        .build())
                .toList();
    }

    synchronized List<SimulatedSession> runningConfigs() {
        return connections.stream().map(SimulatedConnection::running).toList();
    }

    synchronized SessionDetail getSession(SessionRef ref) {
        SimulatedConnection connection = connection(ref);
        return SessionDetail.builder()
                .summary(connection.summary())
                .incomingSeqNum(connection.running().incomingSeqNum)
                .outgoingSeqNum(connection.running().outgoingSeqNum)
                .configs(connection.describeConfigs())
                .sessionSettingsStore(SETTINGS_STORE)
                .build();
    }

    JsonNode getEmitters() {
        return SimulatedEmitters.of(connections.stream().map(SimulatedConnection::emitters).toList());
    }

    synchronized JsonNode getSessionSettings(SessionRef ref) {
        return connection(ref).running().settings;
    }

    synchronized void updateSessionSettings(SessionRef ref, JsonNode settings) {
        connection(ref).replaceSettings(settings);
    }

    synchronized void removeSession(SessionRef ref) {
        connections.remove(connection(ref));
    }

    synchronized void logon(SessionRef ref) {
        connection(ref).running().logon();
    }

    synchronized void logout(SessionRef ref) {
        connection(ref).running().logout();
    }

    synchronized void reset(SessionRef ref, ResetMode mode) {
        SimulatedSession session = connection(ref).running();
        switch (mode) {
            case LOGOUT_LOGON_RESET_NUM_FLAG -> {
                if (session.type != SessionType.INITIATOR) {
                    throw GatewayException.rejected(mode + " needs an initiator session: only the initiator sends the Logon carrying the reset");
                }
                session.resetSeqNums(1);
                session.reconnect();
            }
            case RESET_SEQUENCE -> session.resetSeqNums(1);
            case RESET_SEQUENCE_IN_SESSION -> {
                requireLoggedIn(session, mode.name());
                session.resetSeqNums(2);
            }
        }
    }

    synchronized void activate(SessionRef ref, String configId) {
        SimulatedConnection connection = connection(ref);
        if (connection.type != SessionType.INITIATOR) {
            throw GatewayException.rejected("Only an initiator session has backup configs to switch to");
        }
        connection.activate(configId);
    }

    synchronized void setSeqNums(SessionRef ref, Long incoming, Long outgoing) {
        SimulatedSession session = connection(ref).running();
        if (incoming != null) {
            session.incomingSeqNum = incoming;
        }
        if (outgoing != null) {
            session.outgoingSeqNum = outgoing;
        }
    }

    synchronized void sendMessage(SessionRef ref, String message, char separator, boolean possDup) {
        SimulatedSession session = connection(ref).running();
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

    synchronized boolean hasConfig(String group, String configId) {
        return connections.stream().anyMatch(c -> c.group.equals(group) && c.hasConfig(configId));
    }

    private static void requireLoggedIn(SimulatedSession session, String operation) {
        if (!session.loggedIn) {
            throw GatewayException.rejected(operation + " needs session '" + session.group + "." + session.id + "' to be logged in");
        }
    }

    private SimulatedConnection connection(SessionRef ref) {
        return connections.stream()
                .filter(c -> c.group.equals(ref.getGroup()) && c.id.equals(ref.getSessionId()))
                .findFirst()
                .orElseThrow(() -> GatewayException.unknownSession(ref));
    }
}
