package org.lolaf.staffix.admin.server.simulator;

import lombok.Builder;
import org.lolaf.staffix.admin.server.sessions.FixIdentity;
import org.lolaf.staffix.admin.server.sessions.SessionState;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import tools.jackson.databind.JsonNode;

/**
 * Mutable state of one config of a simulated session (each keeps its own sequence numbers); guarded by its
 * {@link SimulatedEngine}'s lock. It logs on as soon as it should, so a test sees an operation's effect at once.
 */
class SimulatedSession {

    final String id;
    final String group;
    final FixIdentity identity;
    final SessionType type;
    JsonNode settings;
    /**
     * {@code host:port} an initiator dials; null for an acceptor.
     */
    final String connectAddress;

    boolean loggedIn;
    boolean withinSessionTime;
    SessionState desiredState;
    long incomingSeqNum = 1;
    long outgoingSeqNum = 1;

    @Builder
    private SimulatedSession(String id, String group, FixIdentity identity, SessionType type, JsonNode settings,
                             String connectAddress, boolean withinSessionTime, SessionState desiredState) {
        this.id = id;
        this.group = group;
        this.identity = identity;
        this.type = type;
        this.settings = settings;
        this.connectAddress = connectAddress;
        this.withinSessionTime = withinSessionTime;
        this.desiredState = desiredState == null ? SessionState.LOGGED_IN : desiredState;
        reconnect();
    }

    void logon() {
        desiredState = SessionState.LOGGED_IN;
        reconnect();
    }

    void logout() {
        desiredState = SessionState.LOGGED_OUT;
        loggedIn = false;
    }

    /**
     * Logs on again when it should be logged on, as after a reset or a switch to it.
     */
    void reconnect() {
        loggedIn = withinSessionTime && desiredState == SessionState.LOGGED_IN;
    }

    void resetSeqNums(long next) {
        incomingSeqNum = next;
        outgoingSeqNum = next;
    }
}
