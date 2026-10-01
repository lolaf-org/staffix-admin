package org.lolaf.staffix.admin.server.simulator;

import lombok.Builder;
import org.lolaf.staffix.admin.server.sessions.FixIdentity;
import org.lolaf.staffix.admin.server.sessions.SessionState;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import tools.jackson.databind.JsonNode;

import java.util.Random;

/**
 * Mutable state of one config of a simulated session (each keeps its own sequence numbers); guarded by its
 * {@link SimulatedEngine}'s lock.
 */
class SimulatedSession {

    private static final int RECONNECT_TICKS = 2;

    final String id;
    final String group;
    final FixIdentity identity;
    final SessionType type;
    final JsonNode settings;
    /**
     * {@code host:port} an initiator dials; null for an acceptor.
     */
    final String connectAddress;
    /**
     * Drops now and then, to show the colours changing on their own.
     */
    final boolean flaky;
    /**
     * When positive, the session time window opens and closes every that many ticks.
     */
    final int sessionTimeCycleTicks;

    boolean loggedIn;
    boolean withinSessionTime;
    SessionState desiredState;
    long incomingSeqNum = 1;
    long outgoingSeqNum = 1;
    private int ticksToLogon = RECONNECT_TICKS;
    private int ticksInCycle;

    @Builder
    private SimulatedSession(String id, String group, FixIdentity identity, SessionType type, JsonNode settings,
                             String connectAddress,
                             boolean flaky, int sessionTimeCycleTicks, boolean withinSessionTime,
                             SessionState desiredState) {
        this.id = id;
        this.group = group;
        this.identity = identity;
        this.type = type;
        this.settings = settings;
        this.connectAddress = connectAddress;
        this.flaky = flaky;
        this.sessionTimeCycleTicks = sessionTimeCycleTicks;
        this.withinSessionTime = withinSessionTime;
        this.desiredState = desiredState == null ? SessionState.LOGGED_IN : desiredState;
    }

    void tick(Random random) {
        advanceSessionTime();
        if (loggedIn) {
            exchangeTraffic(random);
            if (!withinSessionTime) {
                loggedIn = false;
            } else if (flaky && random.nextInt(40) == 0) {
                drop(2 + random.nextInt(6));
            }
        } else if (shouldBeLoggedIn() && --ticksToLogon <= 0) {
            loggedIn = true;
        }
    }

    void logon() {
        desiredState = SessionState.LOGGED_IN;
        ticksToLogon = 1;
    }

    void logout() {
        desiredState = SessionState.LOGGED_OUT;
        loggedIn = false;
    }

    void drop(int ticksBeforeReconnect) {
        loggedIn = false;
        ticksToLogon = ticksBeforeReconnect;
    }

    void resetSeqNums(long next) {
        incomingSeqNum = next;
        outgoingSeqNum = next;
    }

    private boolean shouldBeLoggedIn() {
        return withinSessionTime && desiredState == SessionState.LOGGED_IN;
    }

    private void advanceSessionTime() {
        if (sessionTimeCycleTicks > 0 && ++ticksInCycle >= sessionTimeCycleTicks) {
            ticksInCycle = 0;
            withinSessionTime = !withinSessionTime;
            ticksToLogon = RECONNECT_TICKS;
        }
    }

    private void exchangeTraffic(Random random) {
        incomingSeqNum += random.nextInt(4);
        outgoingSeqNum += random.nextInt(4);
    }
}
