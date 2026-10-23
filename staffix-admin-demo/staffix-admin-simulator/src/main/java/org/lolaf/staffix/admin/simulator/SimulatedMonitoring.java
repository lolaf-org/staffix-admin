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
package org.lolaf.staffix.admin.simulator;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringContext;
import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.plugins.FixSessionsPluginSettings;
import org.lolaf.staffix.monitoring.micrometer.MicrometerMonitoringManager;
import org.lolaf.staffix.monitoring.micrometer.MicrometerMonitoringManagerSettings;
import org.lolaf.staffix.plugins.async.AsyncFixSessionsPluginSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * staffix's own Micrometer monitoring for the simulated sessions, on an in-memory registry: it describes their meters
 * exactly as a real engine does, and exports nothing. Sessions of a non-client monitoring also carry the application's
 * custom pricing timer, recorded per venue.
 */
final class SimulatedMonitoring {

    static final String PRICING_TIMER = "orders.pricing";
    static final String SENT_COUNTER = "orders.sent";
    static final String VENUE = "venue";
    static final List<String> VENUES = List.of("xpar", "xlon");

    private final String engineId;
    private final Map<String, MicrometerMonitoringManager> managers = new HashMap<>();
    private final Map<FixSessionId, Optional<FixSessionsMonitoringContext>> contexts = new HashMap<>();

    SimulatedMonitoring(String engineId, List<FixSessionsPluginSettings<?>> plugins) {
        this.engineId = engineId;
        plugins.forEach(plugin -> {
            FixSessionsPluginSettings<?> settings = plugin instanceof AsyncFixSessionsPluginSettings async ? async.getDelegateSettings() : plugin;
            if (settings instanceof MicrometerMonitoringManagerSettings micrometer) {
                MicrometerMonitoringManager manager = new MicrometerMonitoringManager(micrometer.toBuilder()
                        .meterRegistrySupplier(SimpleMeterRegistry::new)
                        .build());
                manager.start();
                managers.put(plugin.getInstanceId(), manager);
            }
        });
    }

    /**
     * Created on first use, as staffix creates it when the session is.
     *
     * @param lock what the engine changes its sessions under, held by the caller
     */
    Optional<FixSessionsMonitoringContext> context(SimulatedSession simulated, Object lock) {
        return contexts.computeIfAbsent(simulated.fixSessionId(), id -> {
            String instanceId = simulated.settings.getFixSessionPluginsInstanceIds().get(FixSessionsMonitoringManager.class);
            MicrometerMonitoringManager manager = instanceId == null ? null : managers.get(instanceId);
            if (manager == null) {
                return Optional.empty();
            }
            Optional<FixSessionsMonitoringContext> context = manager.onSessionCreated(engineId, engineId,
                            SimulatedFixSession.of(simulated, lock, Optional::empty), List.of(), List.of())
                    .flatMap(listener -> listener.getPluginContext());
            if (!instanceId.startsWith("clients")) {
                context.ifPresent(monitoring -> VENUES.forEach(venue -> {
                    monitoring.getTimer(PRICING_TIMER, "Time to price an order before it is sent to the venue.", Map.of(VENUE, venue));
                    monitoring.getCounter(SENT_COUNTER, "Orders sent to the venue.", Map.of(VENUE, venue));
                }));
            }
            return context;
        });
    }
}
