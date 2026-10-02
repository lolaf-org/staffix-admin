package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConnectorsConfiguration {

    @Bean
    ConnectorStore connectorStore(AdminProperties properties) {
        return new YamlConnectorStore(properties.getDataDir());
    }

    @Bean
    Connectors connectors(ConnectorStore store, ObjectProvider<MetricsSourceFactory<?>> metricsFactories,
                          ObjectProvider<LogsSourceFactory<?>> logsFactories) {
        return new Connectors(store, new SettingsBinder(System::getenv), metricsFactories.orderedStream().toList(),
                logsFactories.orderedStream().toList());
    }
}
