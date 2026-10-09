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

import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.server.connectors.Emitters;
import org.lolaf.staffix.admin.server.connectors.EngineTelemetry;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;

/**
 * Replaces the engines and the telemetry backends with simulated ones, for the tests running the whole console under
 * the {@code mock} profile. Demos run real engines instead: see {@code staffix-admin-simulator}.
 */
@Configuration
@Profile("mock")
public class SimulatorConfig {

    @Bean
    @Primary
    SimulatedEngineGateway engineGateway(JsonMapper jsonMapper) {
        return new SimulatedEngineGateway(new SimulationScenario(jsonMapper).engines());
    }

    @Bean
    @Primary
    EngineTelemetry simulatedTelemetry(SimulatedEngineGateway gateway) {
        MetricsSource metrics = new SimulatedMetricsSource(gateway);
        LogsSource logs = new SimulatedLogsSource(gateway);
        return new EngineTelemetry() {
            @Override
            public List<MetricsSource> metrics(String engineId, Collection<Emitters> emitters) {
                return List.of(metrics);
            }

            @Override
            public List<LogsSource> logs(String engineId, Collection<Emitters> emitters) {
                return List.of(logs);
            }

            @Override
            public List<MetricsSource> allMetrics(String engineId) {
                return List.of(metrics);
            }

            @Override
            public List<LogsSource> allLogs(String engineId) {
                return List.of(logs);
            }
        };
    }

    @Bean
    SimulatedAnnouncer simulatedAnnouncer(SimulatedEngineGateway gateway, EngineRegistry registry) {
        return new SimulatedAnnouncer(gateway, registry);
    }
}
