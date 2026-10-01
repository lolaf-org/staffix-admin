package org.lolaf.staffix.admin.server.simulator;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Shared by the simulated metrics and logs: values derive from the instant and the session, so a chart or a log
 * search shows the same data each time it is refreshed.
 */
@UtilityClass
class SimulatedTelemetry {

    static double noise(String key, long epochSecond) {
        return new SplittableRandom(key.hashCode() * 1_000_003L ^ epochSecond).nextDouble();
    }

    static List<SessionSummary> sessions(SimulatedEngineGateway gateway, String instanceId, String sessionId) {
        return gateway.listSessions(instanceId).stream()
                .map(SessionGroup::getSessions)
                .flatMap(List::stream)
                .filter(s -> sessionId == null || s.getId().equals(sessionId))
                .toList();
    }
}
