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
 * Replaces real engines with simulated ones, for UI work and demos without a running staffix.
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
    SimulationTicker simulationTicker(SimulatedEngineGateway gateway,
                                      EngineRegistry registry) {
        return new SimulationTicker(gateway, registry);
    }
}
