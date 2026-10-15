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
package org.lolaf.staffix.admin.server.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lolaf.staffix.admin.server.connectors.ConnectorKind;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleSchemaTest {

    private static final SchemaValidator VALIDATOR = new SchemaValidator(ConsoleSchema.of(ConfigSchemas.connectorKinds()));

    @Test
    void findsThePluginsKinds() {
        assertThat(ConfigSchemas.connectorKinds()).extracting(ConnectorKind::getKind).contains("loki", "prometheus");
    }

    @Test
    void acceptsWhatTheConsoleReads() {
        assertThat(VALIDATOR.errors("""
                connectors:
                  - id: alpha-prometheus
                    name: Alpha Prometheus
                    kind: prometheus
                    settings:
                      url: ${env:PROMETHEUS_URL}
                      timeout: 3s
                      timer-base-unit: MICROSECONDS
                      minRateWindow: 10000
                  - id: alpha-loki
                    name: Alpha Loki
                    kind: loki
                    settings:
                      url: http://loki:3100
                      indexedLabels: fix_eid, fix_sg
                      tenant: enc:v1:abc
                  - id: beta
                    name: Beta
                    kind: beta-kind
                    settings:
                      anything: goes
                defaults:
                  metrics: alpha-prometheus
                  logs: none
                engines:
                  alpha-gateway:
                    defaults:
                      logs: alpha-loki
                    loggers:
                      clients-otlp: alpha-loki
                    monitors:
                      clients-metrics: none
                trace:
                  linkingTags: [ 11, 37 ]
                  keyFields:
                    - tag: 55
                      name: Symbol
                """)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"console-prometheus-loki.yaml", "console-es.yaml", "console-clickhouse.yaml"})
    void acceptsTheSandboxConfigurations(String file) {
        assertThat(VALIDATOR.errors(Path.of("../staffix-admin-sandbox", file))).isEmpty();
    }

    @Test
    void refusesAMisspelledSetting() {
        assertThat(VALIDATOR.errors(prometheus("url: http://prometheus:9090\n      timout: 3s"))).first().asString().contains("'timout' is not defined");
    }

    @Test
    void refusesAMissingRequiredSetting() {
        assertThat(VALIDATOR.errors(prometheus("timeout: 3s"))).first().asString().contains("'url' not found");
    }

    @Test
    void refusesAMalformedValue() {
        assertThat(VALIDATOR.errors(prometheus("url: http://prometheus:9090\n      timeout: three seconds"))).first().asString().startsWith("/connectors/0/settings/timeout:");
        assertThat(VALIDATOR.errors(prometheus("url: http://prometheus:9090\n      timerBaseUnit: fortnights"))).first().asString().startsWith("/connectors/0/settings/timerBaseUnit:");
    }

    @Test
    void acceptsAReferenceForAnyValue() {
        assertThat(VALIDATOR.errors(prometheus("url: http://prometheus:9090\n      timeout: ${env:PROMETHEUS_TIMEOUT}"))).isEmpty();
    }

    @Test
    void refusesAConnectorWithoutName() {
        assertThat(VALIDATOR.errors("""
                connectors:
                  - id: alpha
                    kind: beta-kind
                """)).first().asString().contains("'name' not found");
    }

    @Test
    void keepsTheStoreExactNamesOutsideSettings() {
        assertThat(VALIDATOR.errors("trace:\n  linking-tags: [ 11 ]")).first().asString().contains("'linking-tags' is not defined");
        assertThat(VALIDATOR.errors("connector:\n  - id: alpha")).first().asString().contains("'connector' is not defined");
    }

    private static String prometheus(String settings) {
        return """
                connectors:
                  - id: alpha
                    name: Alpha
                    kind: prometheus
                    settings:
                      %s
                """.formatted(settings);
    }
}
