package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConnectorStoreConfig {

    @Bean
    ConnectorStore connectorStore(AdminProperties properties) {
        return new YamlConnectorStore(properties.getDataDir());
    }
}
