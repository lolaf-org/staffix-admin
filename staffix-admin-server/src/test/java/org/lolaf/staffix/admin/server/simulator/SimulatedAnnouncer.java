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
