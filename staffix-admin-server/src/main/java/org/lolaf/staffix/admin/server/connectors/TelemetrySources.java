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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

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
     * @param sessionId a config id: an initiator's backup reads as its session
     * @throws IllegalArgumentException if a connector's stored settings cannot make a source
     */
    public Resolved<MetricsSource> metrics(String engineId, @Nullable String group, @Nullable String sessionId) {
        return resolve(engineId, group, sessionId, emitters -> telemetry.metrics(engineId, emitters), () -> telemetry.allMetrics(engineId));
    }

    /**
     * @param sessionId a config id: an initiator's backup reads as its session
     * @throws IllegalArgumentException if a connector's stored settings cannot make a source
     */
    public Resolved<LogsSource> logs(String engineId, @Nullable String group, @Nullable String sessionId) {
        return resolve(engineId, group, sessionId, emitters -> telemetry.logs(engineId, emitters), () -> telemetry.allLogs(engineId));
    }

    private <S> Resolved<S> resolve(String engineId, @Nullable String group, @Nullable String sessionId,
                                    Function<List<Emitters>, List<S>> sourcesOf, Supplier<List<S>> everySource) {
        Map<Emitters, List<String>> sessionsByEmitters = new HashMap<>();
        groups(engineId).stream()
                .filter(sessions -> group == null || sessions.getGroup().equals(group))
                .forEach(sessions -> sessions.getSessions().stream()
                        .filter(session -> sessionId == null || session.getId().equals(sessionId) || session.getConfigIds().contains(sessionId))
                        .forEach(session -> sessionsByEmitters.computeIfAbsent(Emitters.of(session), emitters -> new ArrayList<>())
                                .add(sessions.getGroup() + "." + session.getId())));
        if (sessionsByEmitters.isEmpty()) {
            return new Resolved<>(everySource.get(), List.of());
        }
        Set<S> sources = new LinkedHashSet<>();
        Set<String> notCollected = new TreeSet<>();
        sessionsByEmitters.forEach((emitters, sessions) -> {
            List<S> found = sourcesOf.apply(List.of(emitters));
            if (found.isEmpty()) {
                notCollected.addAll(sessions);
            }
            sources.addAll(found);
        });
        return new Resolved<>(List.copyOf(sources), List.copyOf(notCollected));
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

    /**
     * @param notCollected the sessions in scope, as {@code group.session}, no source holds the telemetry of; empty
     *                     when their emitters are unknown
     */
    public record Resolved<S>(List<S> sources, List<String> notCollected) {
    }

    private record Known(Instant at, List<SessionGroup> groups) {
    }
}
