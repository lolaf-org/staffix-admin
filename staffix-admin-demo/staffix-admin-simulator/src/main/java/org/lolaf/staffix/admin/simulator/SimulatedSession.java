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
package org.lolaf.staffix.admin.simulator;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.session.FixSessionDesiredState;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.FixSessionStatus;

import java.util.Random;

/**
 * One config of a session and the state a real engine would hold for it, advanced once a second by
 * {@link #tick(Random)}.
 */
final class SimulatedSession {

    private static final int RECONNECT_TICKS = 2;

    FixSessionSettings settings;
    @Nullable
    private FixSessionSettings pendingSettings;
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
    FixSessionDesiredState desiredState;
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
     * As staffix: a live session keeps its settings until it next connects unless restarted; a disconnected one is
     * rebuilt at once.
     */
    void update(FixSessionSettings newSettings, boolean restart) {
        if (loggedIn && !restart) {
            pendingSettings = newSettings;
        } else {
            rebuildOn(newSettings);
            drop(RECONNECT_TICKS);
        }
    }

    boolean hasPendingSettingsUpdate() {
        return pendingSettings != null;
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
        } else if (withinSessionTime && desiredState.equals(FixSessionDesiredState.LOGGED_IN) && --ticksToLogon <= 0) {
            logOn();
        }
    }

    private void logOn() {
        if (pendingSettings != null) {
            rebuildOn(pendingSettings);
        }
        loggedIn = desiredState.equals(FixSessionDesiredState.LOGGED_IN);
    }

    private void rebuildOn(FixSessionSettings newSettings) {
        if (!newSettings.getDesiredSessionState().equals(settings.getDesiredSessionState())) {
            desiredState = newSettings.getDesiredSessionState();
        }
        settings = newSettings;
        pendingSettings = null;
    }

    void logon() {
        desiredState = FixSessionDesiredState.LOGGED_IN;
        ticksToLogon = 1;
    }

    void logout() {
        desiredState = FixSessionDesiredState.LOGGED_OUT;
        loggedIn = false;
    }

    void drop(int ticksBeforeReconnect) {
        loggedIn = false;
        ticksToLogon = ticksBeforeReconnect;
    }

    FixSessionStatus status() {
        if (loggedIn) {
            return FixSessionStatus.LOGGED_IN;
        }
        if (desiredState.equals(FixSessionDesiredState.LOGGED_OUT)) {
            return FixSessionStatus.LOGGED_OUT_BY_OPERATOR;
        }
        return withinSessionTime ? FixSessionStatus.LOGGED_OUT_INSIDE_SESSION_TIME : FixSessionStatus.LOGGED_OUT_OUTSIDE_SESSION_TIME;
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
