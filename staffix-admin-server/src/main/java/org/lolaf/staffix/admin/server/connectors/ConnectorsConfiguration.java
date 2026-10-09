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
package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.lolaf.staffix.admin.connector.SecretResolver;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.lolaf.staffix.admin.store.ConsoleConfigStore;
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
    Connectors connectors(ObjectProvider<ConsoleConfigStore> stores, ObjectProvider<SecretResolver> resolvers, SecretCipher cipher,
                          ObjectProvider<MetricsSourceFactory<?>> metricsFactories, ObjectProvider<LogsSourceFactory<?>> logsFactories) {
        ConsoleConfigStore store = stores.getIfAvailable(() -> {
            throw new IllegalStateException("No console configuration store: put a ConsoleConfigStore plugin, such as staffix-admin-store-yaml, in plugins/");
        });
        return new Connectors(store, new SettingsBinder(resolvers.orderedStream().toList(), cipher), new ConnectorSecrets(cipher),
                metricsFactories.orderedStream().toList(), logsFactories.orderedStream().toList());
    }
}
