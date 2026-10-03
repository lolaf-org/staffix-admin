package org.lolaf.staffix.admin.server.simulator;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;

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

    /**
     * A config that exists but does not run yields nothing, as a real backend holds nothing for it.
     */
    static List<SimulatedSession> runningConfigs(SimulatedEngineGateway gateway, String engineId, String group, String configId) {
        SimulatedEngine engine = gateway.engine(engineId);
        if (configId != null && engine.listGroups().stream()
                .filter(g -> g.getGroup().equals(group))
                .map(SessionGroup::getSessions)
                .flatMap(List::stream)
                .noneMatch(s -> s.getConfigIds().contains(configId))) {
            throw GatewayException.unknownSession(SessionRef.of(engineId, group, configId));
        }
        return engine.runningConfigs().stream()
                .filter(s -> group == null || s.group.equals(group))
                .filter(s -> configId == null || s.id.equals(configId))
                .toList();
    }

    static String key(SimulatedSession session) {
        return session.group + "." + session.id;
    }
}
