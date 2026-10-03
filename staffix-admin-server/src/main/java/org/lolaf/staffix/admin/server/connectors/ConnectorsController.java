package org.lolaf.staffix.admin.server.connectors;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.lolaf.staffix.admin.server.audit.AuditLog;
import org.lolaf.staffix.admin.server.engines.Engine;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorsConfig;
import org.lolaf.staffix.admin.store.StaleConnectorsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Secrets never leave the console: they read {@value ConnectorSecrets#SET} or their reference, and one sent back as
 * {@value ConnectorSecrets#SET} keeps its stored value.
 */
@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorsController {

    private static final String WRITER = "hasRole('WRITER')";

    private final Connectors connectors;
    private final EngineRegistry registry;
    private final EngineGateway gateway;
    private final AuditLog auditLog;
    private final EngineEmitterSettings emitterSettings;

    @GetMapping
    public ConnectorsConfig getConnectors() {
        return redacted(connectors.load());
    }

    /**
     * Replaces every connector and assignment; {@code version} must be the one loaded, so a concurrent change is not
     * overwritten.
     */
    @PreAuthorize(WRITER)
    @PutMapping
    public ConnectorsConfig saveConnectors(@RequestBody ConnectorsConfig config) {
        ConnectorsConfig before = connectors.load();
        return redacted(auditLog.record("connectors: save", () -> connectors.save(config),
                saved -> ConnectorChanges.describe(before, saved)));
    }

    @GetMapping("/kinds")
    public List<ConnectorKind> getKinds() {
        return connectors.describeKinds();
    }

    @PreAuthorize(WRITER)
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testConnector(@RequestBody Connector connector) {
        connectors.testConnection(connector);
    }

    /**
     * Announced engines, and those only the stored connectors name.
     */
    @GetMapping("/engines")
    public List<EngineEmitters> getEngines() {
        Map<String, Engine> announced = registry.list().stream().collect(Collectors.toMap(Engine::getEngineId, Function.identity()));
        Set<String> engineIds = new TreeSet<>(announced.keySet());
        engineIds.addAll(connectors.load().getEngines().keySet());
        return engineIds.stream().map(engineId -> emitters(engineId, announced.containsKey(engineId))).toList();
    }

    @ExceptionHandler(InvalidConnectorsException.class)
    ProblemDetail invalid(InvalidConnectorsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The connectors are invalid");
        problem.setProperty("problems", e.getProblems());
        return problem;
    }

    @ExceptionHandler(StaleConnectorsException.class)
    ProblemDetail stale(StaleConnectorsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({MetricsSourceException.class, LogsSourceException.class})
    ProblemDetail unreachable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private ConnectorsConfig redacted(ConnectorsConfig config) {
        return config.toBuilder()
                .clearConnectors()
                .connectors(config.getConnectors().stream().map(connectors::redacted).toList())
                .build();
    }

    private EngineEmitters emitters(String engineId, boolean announced) {
        EngineEmitters.EngineEmittersBuilder emitters = EngineEmitters.builder().engineId(engineId).announced(announced);
        List<SessionGroup> groups;
        try {
            groups = announced ? gateway.listGroups(engineId) : List.of();
        } catch (GatewayException unreachable) {
            return emitters.reachable(false).build();
        }
        Map<String, List<String>> byLogger = new TreeMap<>();
        Map<String, List<String>> byMonitor = new TreeMap<>();
        groups.forEach(group -> group.getSessions().forEach(session -> {
            String name = group.getGroup() + "." + session.getId();
            byLogger.computeIfAbsent(session.getMessagesLoggerInstanceId(), logger -> new ArrayList<>()).add(name);
            if (session.getMonitoringInstanceId() == null) {
                emitters.unmonitoredSession(name);
            } else {
                byMonitor.computeIfAbsent(session.getMonitoringInstanceId(), monitor -> new ArrayList<>()).add(name);
            }
        }));
        byLogger.forEach((logger, sessions) -> {
            EngineEmitters.Emitter.EmitterBuilder emitter = EngineEmitters.Emitter.builder().instanceId(logger).sessions(sessions).exports(true);
            emitterSettings.logger(engineId, logger).ifPresent(settings -> emitter
                    .types(settings.types())
                    .otlpEndpoint(settings.otlpEndpoint())
                    .exports(settings.exports()));
            emitters.logger(emitter.build());
        });
        byMonitor.forEach((monitor, sessions) -> {
            EngineEmitters.Emitter.EmitterBuilder emitter = EngineEmitters.Emitter.builder().instanceId(monitor).sessions(sessions).exports(true);
            emitterSettings.monitor(engineId, monitor).ifPresent(settings -> emitter
                    .types(settings.types())
                    .otlpEndpoint(settings.otlpEndpoint()));
            emitters.monitor(emitter.build());
        });
        return emitters.reachable(announced).build();
    }
}
