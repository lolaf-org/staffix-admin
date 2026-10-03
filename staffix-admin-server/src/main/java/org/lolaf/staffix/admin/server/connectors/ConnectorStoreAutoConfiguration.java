package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * The YAML file store, unless the application or a plugin defines a {@link ConnectorStore}. A plugin replacing it
 * declares its auto-configuration {@code before} this one.
 */
@AutoConfiguration
public class ConnectorStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConnectorStore.class)
    ConnectorStore connectorStore(AdminProperties properties) {
        return new YamlConnectorStore(properties.getDataDir());
    }
}
