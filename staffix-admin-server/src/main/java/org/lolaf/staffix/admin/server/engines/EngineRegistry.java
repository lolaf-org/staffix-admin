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
    private final EngineProbe probe;
    private final Clock clock;

    /**
     * A known engine announcing another URL or token must answer there with it, and, when it moved, no longer answer
     * at its current URL: any ENGINE user could otherwise point a live engine's operations at a server of their own.
     *
     * @throws GatewayException if TLS is required and the engine announces a plain {@code http} URL, or the
     *                          announcement would take over another engine
     */
    public void announce(EngineAnnouncement announcement) {
        if (properties.isRequireEngineTls() && !"https".equalsIgnoreCase(announcement.getBaseUrl().getScheme())) {
            log.warn("Engine {} refused: it announced {}, and staffix.admin.require-engine-tls is set",
                    announcement.getEngineId(), announcement.getBaseUrl());
            throw GatewayException.invalid("Engine '" + announcement.getEngineId() + "' must be served over https");
        }
        RegisteredEngine current = engines.get(announcement.getEngineId());
        if (current != null && (!current.getBaseUrl().equals(announcement.getBaseUrl())
                || !current.getToken().equals(announcement.getToken()))) {
            verifyChange(current, announcement);
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

    private void verifyChange(RegisteredEngine current, EngineAnnouncement announcement) {
        String engineId = announcement.getEngineId();
        if (!current.getBaseUrl().equals(announcement.getBaseUrl()) && isReachable(current)
                && probe.answersAs(current.getBaseUrl(), current.getToken(), engineId)) {
            log.warn("Engine {} refused at {}: it still answers at {}", engineId, announcement.getBaseUrl(), current.getBaseUrl());
            throw GatewayException.rejected("Engine '" + engineId + "' still answers at its registered URL");
        }
        if (!probe.answersAs(announcement.getBaseUrl(), announcement.getToken(), engineId)) {
            log.warn("Engine {} refused: it does not answer at {} with the token it announced", engineId, announcement.getBaseUrl());
            throw GatewayException.invalid("Engine '" + engineId + "' does not answer at " + announcement.getBaseUrl()
                    + " with the token it announced");
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
