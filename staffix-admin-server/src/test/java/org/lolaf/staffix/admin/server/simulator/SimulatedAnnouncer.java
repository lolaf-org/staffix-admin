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
package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.lolaf.staffix.admin.server.onboarding.EngineAnnouncement;
import org.springframework.context.SmartLifecycle;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Announces the simulated engines, as real ones would, often enough that they stay reachable however long the
 * tests sharing the context run.
 */
@RequiredArgsConstructor
class SimulatedAnnouncer implements SmartLifecycle {

    private static final long INTERVAL_SECONDS = 30;

    private final SimulatedEngineGateway gateway;
    private final EngineRegistry registry;
    private ScheduledExecutorService executor;

    @Override
    public void start() {
        announce();
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("simulator").daemon().factory());
        executor.scheduleAtFixedRate(this::announce, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        executor.shutdownNow();
        executor = null;
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }

    private void announce() {
        gateway.engines().forEach(engine -> registry.announce(EngineAnnouncement.builder()
                .engineId(engine.engineId)
                .baseUrl(URI.create("simulated://" + engine.engineId))
                .token("simulated")
                .staffixVersion(engine.staffixVersion)
                .build()));
    }
}
