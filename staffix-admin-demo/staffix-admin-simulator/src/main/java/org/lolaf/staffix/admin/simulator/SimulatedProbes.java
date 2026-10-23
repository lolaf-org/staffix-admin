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
import org.lolaf.staffix.api.alerting.Alert;
import org.lolaf.staffix.api.alerting.AlertLevel;
import org.lolaf.staffix.api.alerting.ProbeDefinition;
import org.lolaf.staffix.api.alerting.ProbeState;
import org.lolaf.staffix.api.session.FixSessionStatus;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The probes of one simulated session, evaluated on the simulator's one-second tick. Every session has staffix's
 * {@code session-down}; some also have a probe whose alert cycles on a fixed schedule, offset per session, so the
 * demo always has alerts being raised, changing level and clearing. {@code large-order} stands for an alert raised
 * from application code: the only one an operator can clear by hand, until its next cycle raises it again. As in
 * staffix, the application's probes are suspended while the session is not logged in, the others always evaluated.
 */
final class SimulatedProbes {

    static final Duration SESSION_DOWN_DELAY = Duration.ofSeconds(10);
    private static final int ORDER_FLOW_CYCLE = 120;
    private static final int QUEUE_CYCLE = 90;
    private static final int LARGE_ORDER_CYCLE = 150;
    private static final String LARGE_ORDER = "large-order";

    private final List<Probe> probes = new ArrayList<>();
    /**
     * Shifts the session's cycles, so the sessions' alerts do not all change at the same second.
     */
    private final int offset;
    private int downTicks;
    private long lastTick;
    private long largeOrderClearedCycle = -1;

    SimulatedProbes(int sessionIndex) {
        probes.add(new Probe(ProbeDefinition.builder()
                .name("session-down")
                .description("Logged out inside its session time for longer than " + SESSION_DOWN_DELAY.toSeconds() + "s")
                .evaluationInterval(Duration.ofSeconds(1))
                .onlyWhileLoggedIn(false)
                .build()));
        if (sessionIndex % 3 == 0) {
            probes.add(new Probe(ProbeDefinition.builder()
                    .name("order-flow")
                    .description("Time since the last NewOrderSingle")
                    .evaluationInterval(Duration.ofSeconds(1))
                    .reminderInterval(Duration.ofMinutes(1))
                    .runbookUrl(URI.create("https://wiki.example.com/runbooks/order-flow"))
                    .build()));
        }
        if (sessionIndex % 4 == 1) {
            probes.add(new Probe(ProbeDefinition.builder()
                    .name("async-store-queue")
                    .description("Messages waiting in the async store queue to be persisted")
                    .evaluationInterval(Duration.ofSeconds(10))
                    .onlyWhileLoggedIn(false)
                    .build()));
        }
        if (sessionIndex % 5 == 2) {
            probes.add(new Probe(ProbeDefinition.builder()
                    .name(LARGE_ORDER)
                    .description("NewOrderSingle above the quantity limit")
                    .evaluationInterval(Duration.ofSeconds(1))
                    .label("team", "trading_desk")
                    .runbookUrl(URI.create("https://wiki.example.com/runbooks/large-order"))
                    .build()));
        }
        this.offset = sessionIndex * 37;
    }

    List<ProbeDefinition> definitions() {
        return probes.stream().map(probe -> probe.definition).toList();
    }

    @Nullable URI runbookUrl(String probeName) {
        return probes.stream().filter(probe -> probe.definition.getName().equals(probeName)).findFirst()
                .flatMap(probe -> probe.definition.getRunbookUrl()).orElse(null);
    }

    List<ProbeState> states() {
        return probes.stream().map(Probe::state).filter(state -> state != null).toList();
    }

    /**
     * Accepted only for {@code large-order} in alert; the others are refused, as staffix refuses a polled condition.
     *
     * @throws IllegalArgumentException if the session has no such probe
     */
    boolean clear(String probeName) {
        Probe probe = probes.stream().filter(candidate -> candidate.definition.getName().equals(probeName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No probe named " + probeName));
        if (!probeName.equals(LARGE_ORDER) || probe.alert == null) {
            return false;
        }
        largeOrderClearedCycle = (lastTick + offset) / LARGE_ORDER_CYCLE;
        probe.clearRequested = true;
        return true;
    }

    /**
     * Returns the states whose level changed, raised or cleared, which is what staffix reports as a transition.
     */
    List<ProbeState> tick(long tick, FixSessionStatus status, Instant now) {
        lastTick = tick;
        downTicks = status == FixSessionStatus.LOGGED_OUT_INSIDE_SESSION_TIME ? downTicks + 1 : 0;
        List<ProbeState> transitions = new ArrayList<>();
        boolean loggedIn = status == FixSessionStatus.LOGGED_IN;
        for (Probe probe : probes) {
            if (probe.definition.isOnlyWhileLoggedIn() && !loggedIn) {
                if (probe.suspend()) {
                    transitions.add(probe.state());
                }
            } else if (probe.evaluate(alert(probe.definition.getName(), tick), now)) {
                transitions.add(probe.state(probe.clearRequested && probe.alert == null));
            }
            probe.clearRequested = false;
        }
        return transitions;
    }

    private @Nullable Alert alert(String probe, long tick) {
        return switch (probe) {
            case "session-down" -> downTicks > SESSION_DOWN_DELAY.toSeconds() ? Alert.critical("down for " + downTicks + "s") : null;
            case "order-flow" -> orderFlow((int) ((tick + offset) % ORDER_FLOW_CYCLE));
            case LARGE_ORDER -> largeOrder(tick + offset);
            default -> queue((int) ((tick + offset) % QUEUE_CYCLE));
        };
    }

    /**
     * Normal for a minute, then a silence growing past the warning and the major thresholds.
     */
    private static @Nullable Alert orderFlow(int phase) {
        int silence = phase - 55;
        if (phase >= 90 && phase < 110) {
            return Alert.major("no order for " + silence + "s");
        }
        return phase >= 60 && phase < 110 ? Alert.warning("no order for " + silence + "s") : null;
    }

    /**
     * Raised for a minute of each cycle, unless an operator cleared it during this one.
     */
    private @Nullable Alert largeOrder(long shiftedTick) {
        long phase = shiftedTick % LARGE_ORDER_CYCLE;
        boolean raised = phase >= 30 && phase < 90 && shiftedTick / LARGE_ORDER_CYCLE != largeOrderClearedCycle;
        return raised ? Alert.major("order of 250000 above the limit of 10000") : null;
    }

    private static @Nullable Alert queue(int phase) {
        return phase >= 50 && phase < 80 ? Alert.warning((250 + phase * 7) + " events queued, WARNING from 250") : null;
    }

    private static final class Probe {

        final ProbeDefinition definition;
        @Nullable Alert alert;
        @Nullable Instant since;
        @Nullable Instant lastEvaluation;
        boolean clearRequested;
        boolean suspended;

        Probe(ProbeDefinition definition) {
            this.definition = definition;
        }

        /**
         * Returns whether an ongoing alert was cleared.
         */
        boolean suspend() {
            suspended = true;
            boolean wasInAlert = alert != null;
            alert = null;
            return wasInAlert;
        }

        boolean evaluate(@Nullable Alert next, Instant now) {
            suspended = false;
            AlertLevel previous = alert == null ? null : alert.getLevel();
            if (alert == null && next != null) {
                since = now;
            }
            alert = next;
            lastEvaluation = now;
            return previous != (next == null ? null : next.getLevel());
        }

        @Nullable ProbeState state() {
            return state(false);
        }

        @Nullable ProbeState state(boolean clearedManually) {
            if (lastEvaluation == null && !suspended) {
                return null;
            }
            return ProbeState.builder()
                    .probeName(definition.getName())
                    .alert(alert)
                    .since(alert == null ? null : since)
                    .lastEvaluation(lastEvaluation)
                    .clearedManually(clearedManually)
                    .suspended(suspended)
                    .build();
        }
    }
}
