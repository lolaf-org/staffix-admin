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

    public void announce(EngineAnnouncement announcement) {
        RegisteredEngine engine = RegisteredEngine.builder()
                .instanceId(announcement.getInstanceId())
                .baseUrl(announcement.getBaseUrl())
                .token(announcement.getToken())
                .staffixVersion(announcement.getStaffixVersion())
                .lastAnnouncedAt(clock.instant())
                .build();
        if (engines.put(engine.getInstanceId(), engine) == null) {
            log.info("Engine {} registered at {}", engine.getInstanceId(), engine.getBaseUrl());
        }
    }

    public List<Engine> list() {
        return engines.values().stream()
                .sorted(Comparator.comparing(RegisteredEngine::getInstanceId))
                .map(this::toEngine)
                .toList();
    }

    public RegisteredEngine get(String instanceId) {
        RegisteredEngine engine = engines.get(instanceId);
        if (engine == null) {
            throw GatewayException.unknownEngine(instanceId);
        }
        return engine;
    }

    private Engine toEngine(RegisteredEngine engine) {
        return Engine.builder()
                .instanceId(engine.getInstanceId())
                .staffixVersion(engine.getStaffixVersion())
                .reachable(isReachable(engine))
                .lastAnnouncedAt(engine.getLastAnnouncedAt())
                .build();
    }

    private boolean isReachable(RegisteredEngine engine) {
        return engine.getLastAnnouncedAt().plus(properties.getEngineHeartbeatTimeout()).isAfter(clock.instant());
    }
}
