package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.lolaf.staffix.admin.connector.SecretResolver;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;

@Configuration
class ConnectorsConfiguration {

    @Bean
    JacksonModule connectorsJacksonModule() {
        return ConnectorsJackson.module();
    }

    @Bean
    Connectors connectors(ConnectorStore store, ObjectProvider<SecretResolver> resolvers, SecretCipher cipher,
                          ObjectProvider<MetricsSourceFactory<?>> metricsFactories, ObjectProvider<LogsSourceFactory<?>> logsFactories) {
        return new Connectors(store, new SettingsBinder(resolvers.orderedStream().toList(), cipher), new ConnectorSecrets(cipher),
                metricsFactories.orderedStream().toList(), logsFactories.orderedStream().toList());
    }
}
