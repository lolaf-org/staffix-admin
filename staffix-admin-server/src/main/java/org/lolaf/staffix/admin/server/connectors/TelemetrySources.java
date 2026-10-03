package org.lolaf.staffix.admin.server.connectors;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The sources holding the telemetry of an engine, one of its groups, or one session, found through the emitters its
 * sessions report. An engine that does not answer is resolved from its last answer, so its history stays readable;
 * one never seen, or a session it no longer has, from every connector the engine may report to.
 */
@Component
@RequiredArgsConstructor
public class TelemetrySources {

    /**
     * Charts send several queries per refresh; one status call serves them all.
     */
    private static final Duration FRESH = Duration.ofSeconds(10);

    private final EngineGateway gateway;
    private final EngineTelemetry telemetry;
    private final Clock clock;
    private final Map<String, Known> lastAnswers = new ConcurrentHashMap<>();

    /**
     * @throws IllegalArgumentException if a connector's stored settings cannot make a source
     */
    public List<MetricsSource> metrics(String engineId, @Nullable String group, @Nullable String sessionId) {
        return emitters(engineId, group, sessionId)
                .map(emitters -> telemetry.metrics(engineId, emitters))
                .orElseGet(() -> telemetry.allMetrics(engineId));
    }

    /**
     * @throws IllegalArgumentException if a connector's stored settings cannot make a source
     */
    public List<LogsSource> logs(String engineId, @Nullable String group, @Nullable String sessionId) {
        return emitters(engineId, group, sessionId)
                .map(emitters -> telemetry.logs(engineId, emitters))
                .orElseGet(() -> telemetry.allLogs(engineId));
    }

    private Optional<Set<Emitters>> emitters(String engineId, @Nullable String group, @Nullable String sessionId) {
        Set<Emitters> emitters = groups(engineId).stream()
                .filter(sessions -> group == null || sessions.getGroup().equals(group))
                .flatMap(sessions -> sessions.getSessions().stream())
                .filter(session -> sessionId == null || session.getId().equals(sessionId))
                .map(Emitters::of)
                .collect(Collectors.toSet());
        return emitters.isEmpty() ? Optional.empty() : Optional.of(emitters);
    }

    private List<SessionGroup> groups(String engineId) {
        Instant now = clock.instant();
        Known last = lastAnswers.get(engineId);
        if (last != null && now.isBefore(last.at().plus(FRESH))) {
            return last.groups();
        }
        try {
            List<SessionGroup> groups = gateway.listGroups(engineId);
            lastAnswers.put(engineId, new Known(now, groups));
            return groups;
        } catch (GatewayException unreachable) {
            return last == null ? List.of() : last.groups();
        }
    }

    private record Known(Instant at, List<SessionGroup> groups) {
    }
}
