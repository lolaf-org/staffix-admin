package org.lolaf.staffix.admin.server.simulator;

import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces real engines with simulated ones, for UI work and demos without a running staffix.
 */
@Configuration
@Profile("mock")
public class SimulatorConfig {

    @Bean
    SimulatedEngineGateway engineGateway(JsonMapper jsonMapper) {
        return new SimulatedEngineGateway(new SimulationScenario(jsonMapper).engines());
    }

    @Bean
    SimulatedMetricsSource metricsSource(SimulatedEngineGateway gateway) {
        return new SimulatedMetricsSource(gateway);
    }

    @Bean
    SimulatedLogsSource logsSource(SimulatedEngineGateway gateway) {
        return new SimulatedLogsSource(gateway);
    }

    @Bean
    SimulationTicker simulationTicker(SimulatedEngineGateway gateway,
                                      EngineRegistry registry) {
        return new SimulationTicker(gateway, registry);
    }
}
