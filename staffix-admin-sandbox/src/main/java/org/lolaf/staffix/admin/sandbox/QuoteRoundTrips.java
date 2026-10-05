package org.lolaf.staffix.admin.sandbox;

import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringContext;
import org.lolaf.staffix.api.monitoring.Timer;
import org.lolaf.staffix.api.monitoring.VoidFixSessionsMonitoringContext;
import org.lolaf.staffix.api.session.FixSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Times each QuoteRequest until its Quote arrives, with an application timer of the session's monitoring, tagged with
 * the venue the session trades with (its group).
 */
final class QuoteRoundTrips {

    static final String TIMER = "quote.round.trip";
    static final String PRICING_TIMER = "quote.pricing";

    private final Map<String, Long> sentAt = new ConcurrentHashMap<>();
    private volatile Timer roundTrip = Timer.VoidTimer.getInstance();

    void onSessionCreated(FixSession session) {
        roundTrip = monitoring(session).getTimer(TIMER, "Time from sending a QuoteRequest to receiving its Quote.",
                Map.of("venue", session.getFixSessionId().getGroup()));
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

    private static FixSessionsMonitoringContext monitoring(FixSession session) {
        return session.getPluginContext(FixSessionsMonitoringContext.class).orElse(VoidFixSessionsMonitoringContext.getInstance());
    }
}
