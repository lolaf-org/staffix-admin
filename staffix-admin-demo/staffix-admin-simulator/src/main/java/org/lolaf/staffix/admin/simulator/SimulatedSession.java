package org.lolaf.staffix.admin.simulator;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.FixSessionState;

import java.util.Random;

/**
 * One config of a session and the state a real engine would hold for it, advanced once a second by
 * {@link #tick(Random)}.
 */
final class SimulatedSession {

    private static final int RECONNECT_TICKS = 2;

    FixSessionSettings settings;
    /**
     * {@code host:port} an initiator dials; null for an acceptor.
     */
    @Nullable
    final String connectAddress;
    /**
     * Drops now and then, so the console's colours change on their own.
     */
    final boolean flaky;
    /**
     * When positive, the session time window opens and closes every that many ticks.
     */
    final int sessionTimeCycleTicks;

    boolean loggedIn;
    boolean withinSessionTime;
    FixSessionState desiredState;
    long incomingSeqNum = 1;
    long outgoingSeqNum = 1;
    private int ticksToLogon = RECONNECT_TICKS;
    private int ticksInCycle;

    SimulatedSession(FixSessionSettings settings, @Nullable String connectAddress, boolean flaky, int sessionTimeCycleTicks,
                     boolean withinSessionTime) {
        this.settings = settings;
        this.connectAddress = connectAddress;
        this.flaky = flaky;
        this.sessionTimeCycleTicks = sessionTimeCycleTicks;
        this.withinSessionTime = withinSessionTime;
        this.desiredState = settings.getDesiredSessionState();
    }

    FixSessionId fixSessionId() {
        return settings.getFixSessionId();
    }

    String group() {
        return settings.getFixSessionId().getGroup();
    }

    String name() {
        return settings.getFixSessionId().getName();
    }

    /**
     * As an engine restarting the session on settings an operator changed.
     */
    void restartOn(FixSessionSettings newSettings) {
        settings = newSettings;
        desiredState = newSettings.getDesiredSessionState();
        drop(RECONNECT_TICKS);
    }

    void tick(Random random) {
        advanceSessionTime();
        if (loggedIn) {
            incomingSeqNum += random.nextInt(4);
            outgoingSeqNum += random.nextInt(4);
            if (!withinSessionTime) {
                loggedIn = false;
            } else if (flaky && random.nextInt(40) == 0) {
                drop(2 + random.nextInt(6));
            }
        } else if (withinSessionTime && desiredState == FixSessionState.LOGGED_IN && --ticksToLogon <= 0) {
            loggedIn = true;
        }
    }

    void logon() {
        desiredState = FixSessionState.LOGGED_IN;
        ticksToLogon = 1;
    }

    void logout() {
        desiredState = FixSessionState.LOGGED_OUT;
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

    private void advanceSessionTime() {
        if (sessionTimeCycleTicks > 0 && ++ticksInCycle >= sessionTimeCycleTicks) {
            ticksInCycle = 0;
            withinSessionTime = !withinSessionTime;
            ticksToLogon = RECONNECT_TICKS;
        }
    }
}
