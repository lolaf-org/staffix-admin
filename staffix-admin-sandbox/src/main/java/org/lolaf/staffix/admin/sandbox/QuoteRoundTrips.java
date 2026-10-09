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
package org.lolaf.staffix.admin.sandbox;

import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringContext;
import org.lolaf.staffix.api.monitoring.Gauge;
import org.lolaf.staffix.api.monitoring.Timer;
import org.lolaf.staffix.api.monitoring.VoidFixSessionsMonitoringContext;
import org.lolaf.staffix.api.session.FixSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Times each QuoteRequest until its Quote arrives, with an application timer of the session's monitoring tagged with
 * the venue the session trades with (its group), and publishes how many are pending as a gauge read from them.
 */
final class QuoteRoundTrips {

    static final String TIMER = "quote.round.trip";
    static final String PRICING_TIMER = "quote.pricing";
    static final String PENDING_GAUGE = "quotes.pending";
    static final String LAST_MID_GAUGE = "quote.last.mid";

    private final Map<String, Long> sentAt = new ConcurrentHashMap<>();
    private volatile Timer roundTrip = Timer.VoidTimer.getInstance();

    void onSessionCreated(FixSession session) {
        Map<String, String> venue = Map.of("venue", session.getFixSessionId().getGroup());
        roundTrip = monitoring(session).getTimer(TIMER, "Time from sending a QuoteRequest to receiving its Quote.", venue);
        monitoring(session).getGauge(PENDING_GAUGE, "QuoteRequests sent and not yet quoted.", venue, sentAt::size);
    }

    void sent(String quoteReqId) {
        sentAt.put(quoteReqId, System.nanoTime());
    }

    void quoted(String quoteReqId) {
        Long sent = sentAt.remove(quoteReqId);
        if (sent != null) {
            roundTrip.record(System.nanoTime() - sent, TimeUnit.NANOSECONDS);
        }
    }

    static Timer pricing(FixSession session) {
        return monitoring(session).getTimer(PRICING_TIMER, "Time to price a QuoteRequest and send its Quote.", Map.of());
    }

    static Gauge lastMid(FixSession session) {
        return monitoring(session).getGauge(LAST_MID_GAUGE, "The mid price of the last Quote sent.", Map.of());
    }

    private static FixSessionsMonitoringContext monitoring(FixSession session) {
        return session.getPluginContext(FixSessionsMonitoringContext.class).orElse(VoidFixSessionsMonitoringContext.getInstance());
    }
}
