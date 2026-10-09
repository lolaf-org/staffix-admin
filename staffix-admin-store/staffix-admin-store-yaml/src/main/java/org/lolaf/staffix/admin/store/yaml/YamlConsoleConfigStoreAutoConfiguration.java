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
