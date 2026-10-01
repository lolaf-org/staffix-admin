package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.lolaf.staffix.admin.server.onboarding.EngineAnnouncement;
import org.springframework.context.SmartLifecycle;

import java.net.URI;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Advances the simulation every second and announces the simulated engines, as real ones would.
 */
@RequiredArgsConstructor
class SimulationTicker implements SmartLifecycle {

    private final SimulatedEngineGateway gateway;
    private final EngineRegistry registry;
    private final Random random = new Random();
    private ScheduledExecutorService executor;

    @Override
    public void start() {
        announce();
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("simulator").daemon().factory());
        executor.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
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

    private void tick() {
        gateway.tick(random);
        announce();
    }

    private void announce() {
        gateway.engines().forEach(engine -> registry.announce(EngineAnnouncement.builder()
                .instanceId(engine.instanceId)
                .baseUrl(URI.create("simulated://" + engine.instanceId))
                .token("simulated")
                .staffixVersion(engine.staffixVersion)
                .build()));
    }
}
