package org.lolaf.staffix.admin.server.engines;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.server.onboarding.EngineAnnouncement;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engines that announced themselves. Held in memory only: engines re-announce periodically, so a restart of the
 * admin repopulates it without storing their tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineRegistry {

    private final Map<String, RegisteredEngine> engines = new ConcurrentHashMap<>();
    private final AdminProperties properties;
    private final Clock clock;

    /**
     * @throws GatewayException if TLS is required and the engine announces a plain {@code http} URL
     */
    public void announce(EngineAnnouncement announcement) {
        if (properties.isRequireEngineTls() && !"https".equalsIgnoreCase(announcement.getBaseUrl().getScheme())) {
            log.warn("Engine {} refused: it announced {}, and staffix.admin.require-engine-tls is set",
                    announcement.getEngineId(), announcement.getBaseUrl());
            throw GatewayException.invalid("Engine '" + announcement.getEngineId() + "' must be served over https");
        }
        RegisteredEngine engine = RegisteredEngine.builder()
                .engineId(announcement.getEngineId())
                .baseUrl(announcement.getBaseUrl())
                .token(announcement.getToken())
                .staffixVersion(announcement.getStaffixVersion())
                .lastAnnouncedAt(clock.instant())
                .build();
        if (engines.put(engine.getEngineId(), engine) == null) {
            log.info("Engine {} registered at {}", engine.getEngineId(), engine.getBaseUrl());
        }
    }

    public List<Engine> list() {
        return engines.values().stream()
                .sorted(Comparator.comparing(RegisteredEngine::getEngineId))
                .map(this::toEngine)
                .toList();
    }

    public RegisteredEngine get(String engineId) {
        RegisteredEngine engine = engines.get(engineId);
        if (engine == null) {
            throw GatewayException.unknownEngine(engineId);
        }
        return engine;
    }

    private Engine toEngine(RegisteredEngine engine) {
        return Engine.builder()
                .engineId(engine.getEngineId())
                .staffixVersion(engine.getStaffixVersion())
                .reachable(isReachable(engine))
                .lastAnnouncedAt(engine.getLastAnnouncedAt())
                .build();
    }

    private boolean isReachable(RegisteredEngine engine) {
        return engine.getLastAnnouncedAt().plus(properties.getEngineHeartbeatTimeout()).isAfter(clock.instant());
    }
}
