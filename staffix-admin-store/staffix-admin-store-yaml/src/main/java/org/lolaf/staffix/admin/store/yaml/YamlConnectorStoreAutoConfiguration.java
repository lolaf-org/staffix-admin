package org.lolaf.staffix.admin.store.yaml;

import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Stores the connectors in {@code connectors.yaml} of the console's data directory, unless the application defines
 * another {@link ConnectorStore}.
 */
@AutoConfiguration
public class YamlConnectorStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConnectorStore.class)
    ConnectorStore yamlConnectorStore(@Value("${staffix.admin.data-dir:data}") Path dataDir) {
        return new YamlConnectorStore(dataDir);
    }
}
