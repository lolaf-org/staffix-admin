package org.lolaf.staffix.admin.store.yaml;

import org.lolaf.staffix.admin.store.ConsoleConfigStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Stores the console configuration in {@code console.yaml} of the console's data directory, unless the application
 * defines another {@link ConsoleConfigStore}.
 */
@AutoConfiguration
public class YamlConsoleConfigStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConsoleConfigStore.class)
    ConsoleConfigStore yamlConsoleConfigStore(@Value("${staffix.admin.data-dir:data}") Path dataDir) {
        return new YamlConsoleConfigStore(dataDir);
    }
}
