package org.lolaf.staffix.admin.server.connectors;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The sources holding the telemetry of an engine, one of its groups, or one session, found through the emitters its
 * sessions report. An engine that does not answer is resolved from its last answer, so its history stays readable;
 * one never seen, or a session it no longer has, from every connector the engine may report to. A session whose
 * logger exports nothing a backend reads has its logs not collected, whatever its connector.
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
    private final EngineEmitterSettings emitterSettings;
    private final Clock clock;
    private final Map<String, Known> lastAnswers = new ConcurrentHashMap<>();

    /**
     * @param sessionId a config id: an initiator's backup reads as its session
     * @throws IllegalArgumentException if a connector's stored settings cannot make a source
     */
    public Resolved<MetricsSource> metrics(String engineId, @Nullable String group, @Nullable String sessionId) {
        return resolve(engineId, group, sessionId, emitters -> telemetry.metrics(engineId, List.of(emitters)),
                () -> telemetry.allMetrics(engineId), emitters -> monitor(engineId, emitters).map(EngineEmitterSettings.Monitor::timerBaseUnit).orElse(null));
    }

    /**
     * @param sessionId a config id: an initiator's backup reads as its session
     * @throws IllegalArgumentException if a connector's stored settings cannot make a source
     */
    public Resolved<LogsSource> logs(String engineId, @Nullable String group, @Nullable String sessionId) {
        return resolve(engineId, group, sessionId, emitters -> exportsLogs(engineId, emitters) ? telemetry.logs(engineId, List.of(emitters)) : List.of(),
                () -> telemetry.allLogs(engineId), emitters -> null);
    }

    /**
     * The kinds the monitoring plugins of the scope's sessions measure; every kind when they are unknown.
     *
     * @param sessionId a config id: an initiator's backup reads as its session
     */
    public Set<MetricKind> metricKinds(String engineId, @Nullable String group, @Nullable String sessionId) {
        Map<Emitters, List<String>> sessionsByEmitters = sessionsByEmitters(engineId, group, sessionId);
        if (sessionsByEmitters.isEmpty()) {
            return EnumSet.allOf(MetricKind.class);
        }
        Set<MetricKind> kinds = EnumSet.noneOf(MetricKind.class);
        sessionsByEmitters.keySet().stream()
                .filter(emitters -> emitters.monitoring() != null)
                .forEach(emitters -> kinds.addAll(monitor(engineId, emitters)
                        .map(EngineEmitterSettings.Monitor::kinds)
                        .orElse(EnumSet.allOf(MetricKind.class))));
        return kinds;
    }

    private Optional<EngineEmitterSettings.Monitor> monitor(String engineId, Emitters emitters) {
        return emitters.monitoring() == null ? Optional.empty() : emitterSettings.monitor(engineId, emitters.monitoring());
    }

    private boolean exportsLogs(String engineId, Emitters emitters) {
        return emitterSettings.logger(engineId, emitters.messagesLogger()).map(EngineEmitterSettings.Logger::exports).orElse(true);
    }

    private <S> Resolved<S> resolve(String engineId, @Nullable String group, @Nullable String sessionId,
                                    Function<Emitters, List<S>> sourcesOf, Supplier<List<S>> everySource,
                                    Function<Emitters, @Nullable TimeUnit> timerBaseUnitOf) {
        Map<Emitters, List<String>> sessionsByEmitters = sessionsByEmitters(engineId, group, sessionId);
        if (sessionsByEmitters.isEmpty()) {
            return new Resolved<>(everySource.get(), List.of(), Map.of());
        }
        Set<S> sources = new LinkedHashSet<>();
        Set<String> notCollected = new TreeSet<>();
        Map<S, Set<Optional<TimeUnit>>> unitsBySource = new HashMap<>();
        sessionsByEmitters.forEach((emitters, sessions) -> {
            List<S> found = sourcesOf.apply(emitters);
            if (found.isEmpty()) {
                notCollected.addAll(sessions);
            }
            sources.addAll(found);
            found.forEach(source -> unitsBySource.computeIfAbsent(source, s -> new HashSet<>())
                    .add(Optional.ofNullable(timerBaseUnitOf.apply(emitters))));
        });
        Map<S, TimeUnit> timerBaseUnits = new HashMap<>();
        unitsBySource.forEach((source, units) -> {
            if (units.size() == 1 && units.iterator().next().isPresent()) {
                timerBaseUnits.put(source, units.iterator().next().get());
            }
        });
        return new Resolved<>(List.copyOf(sources), List.copyOf(notCollected), timerBaseUnits);
    }

    private Map<Emitters, List<String>> sessionsByEmitters(String engineId, @Nullable String group, @Nullable String sessionId) {
        Map<Emitters, List<String>> sessionsByEmitters = new HashMap<>();
        groups(engineId).stream()
                .filter(sessions -> group == null || sessions.getGroup().equals(group))
                .forEach(sessions -> sessions.getSessions().stream()
                        .filter(session -> sessionId == null || session.getId().equals(sessionId) || session.getConfigIds().contains(sessionId))
                        .forEach(session -> sessionsByEmitters.computeIfAbsent(Emitters.of(session), emitters -> new ArrayList<>())
                                .add(sessions.getGroup() + "." + session.getId())));
        return sessionsByEmitters;
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
     * @param notCollected   the sessions in scope, as {@code group.session}, no source holds the telemetry of; empty
     *                       when their emitters are unknown
     * @param timerBaseUnits by source, the unit its engines' timers are in, when every monitoring plugin read through
     *                       it says the same
     */
    public record Resolved<S>(List<S> sources, List<String> notCollected, Map<S, TimeUnit> timerBaseUnits) {
    }

    private record Known(Instant at, List<SessionGroup> groups) {
    }
}
