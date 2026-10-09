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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConsoleConfig;
import org.lolaf.staffix.admin.store.ConsoleConfigStoreException;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.EngineMapping;
import org.lolaf.staffix.admin.store.StaleConsoleConfigException;
import org.lolaf.staffix.admin.store.TraceConfig;
import org.lolaf.staffix.admin.store.TraceField;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlConsoleConfigStoreTest {

    private static final ConsoleConfig CONFIG = ConsoleConfig.builder()
            .connector(Connector.builder()
                    .id("alpha-prometheus")
                    .name("Alpha Prometheus")
                    .kind("prometheus")
                    .setting("url", "http://prometheus:9090")
                    .setting("timerBaseUnit", "microseconds")
                    .build())
            .connector(Connector.builder()
                    .id("alpha-loki")
                    .name("Alpha Loki")
                    .kind("loki")
                    .setting("url", "http://loki:3100")
                    .setting("indexedLabels", List.of("fix_eid", "fix_sg"))
                    .setting("tenant", "${env:LOKI_TENANT}")
                    .build())
            .defaults(EngineConnectors.builder().metrics("alpha-prometheus").logs("alpha-loki").build())
            .engine("gamma", EngineMapping.builder().defaults(EngineConnectors.builder().logs(EngineConnectors.NONE).build()).build())
            .engine("beta", EngineMapping.builder()
                    .logger("clients-otlp", EngineConnectors.NONE)
                    .monitor("otlp-metrics", "alpha-prometheus")
                    .build())
            .build();

    @TempDir
    Path dataDir;

    @Test
    void isEmptyBeforeTheFirstSave() {
        assertThat(new YamlConsoleConfigStore(dataDir.resolve("absent")).load()).isEqualTo(ConsoleConfig.EMPTY);
    }

    @Test
    void savesReadableYamlAndReadsItBack() throws IOException {
        YamlConsoleConfigStore store = new YamlConsoleConfigStore(dataDir.resolve("admin"));

        ConsoleConfig saved = store.save(CONFIG);

        assertThat(saved).isEqualTo(CONFIG.toBuilder().version(1).build());
        assertThat(store.load()).isEqualTo(saved);
        assertThat(Files.readString(dataDir.resolve("admin").resolve(YamlConsoleConfigStore.FILE_NAME))).isEqualTo("""
                version: 1
                connectors:
                  - id: alpha-prometheus
                    name: Alpha Prometheus
                    kind: prometheus
                    settings:
                      timerBaseUnit: microseconds
                      url: http://prometheus:9090
                  - id: alpha-loki
                    name: Alpha Loki
                    kind: loki
                    settings:
                      indexedLabels:
                        - fix_eid
                        - fix_sg
                      tenant: "${env:LOKI_TENANT}"
                      url: http://loki:3100
                defaults:
                  metrics: alpha-prometheus
                  logs: alpha-loki
                engines:
                  beta:
                    loggers:
                      clients-otlp: none
                    monitors:
                      otlp-metrics: alpha-prometheus
                  gamma:
                    defaults:
                      logs: none
                """);
        assertThat(dataDir.resolve("admin")).isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    void writesAFileOnlyItsOwnerReads() throws IOException {
        new YamlConsoleConfigStore(dataDir).save(CONFIG);

        assertThat(Files.getPosixFilePermissions(dataDir.resolve(YamlConsoleConfigStore.FILE_NAME)))
                .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    @Test
    void refusesASaveBasedOnAnOlderVersion() {
        YamlConsoleConfigStore store = new YamlConsoleConfigStore(dataDir);
        ConsoleConfig loadedByBoth = store.load();
        store.save(loadedByBoth.toBuilder().connector(CONFIG.getConnectors().getFirst()).build());

        assertThatThrownBy(() -> store.save(loadedByBoth.toBuilder().connector(CONFIG.getConnectors().getLast()).build()))
                .isInstanceOfSatisfying(StaleConsoleConfigException.class, e -> {
                    assertThat(e.getExpectedVersion()).isZero();
                    assertThat(e.getStoredVersion()).isEqualTo(1);
                });
        assertThat(store.load().getConnectors()).containsExactly(CONFIG.getConnectors().getFirst());
    }

    @Test
    void readsAHandWrittenFile() throws IOException {
        Files.writeString(dataDir.resolve(YamlConsoleConfigStore.FILE_NAME), """
                connectors:
                  - id: alpha-loki
                    name: Alpha Loki
                    kind: loki
                    settings:
                      url: http://loki:3100
                defaults:
                  logs: alpha-loki
                """);

        ConsoleConfig config = new YamlConsoleConfigStore(dataDir).load();

        assertThat(config.getVersion()).isZero();
        assertThat(config.getConnectors()).extracting(Connector::getId).containsExactly("alpha-loki");
        assertThat(config.getDefaults()).isEqualTo(EngineConnectors.builder().logs("alpha-loki").build());
        assertThat(config.getEngines()).isEmpty();
        assertThat(config.getTrace()).isNull();
    }

    @Test
    void keepsTheTraceSettingsAnEmptyListIncluded() throws IOException {
        YamlConsoleConfigStore store = new YamlConsoleConfigStore(dataDir);
        TraceConfig trace = TraceConfig.builder()
                .linkingTags(List.of())
                .keyFields(List.of(TraceField.builder().tag(11).name("ClOrdID").build(), TraceField.builder().tag(526).name("SecondaryClOrdID").build()))
                .build();

        ConsoleConfig saved = store.save(ConsoleConfig.builder().trace(trace).build());

        assertThat(store.load()).isEqualTo(saved);
        assertThat(Files.readString(dataDir.resolve(YamlConsoleConfigStore.FILE_NAME))).isEqualTo("""
                version: 1
                defaults: {}
                trace:
                  linkingTags: []
                  keyFields:
                    - tag: 11
                      name: ClOrdID
                    - tag: 526
                      name: SecondaryClOrdID
                """);
    }

    @Test
    void reportsAMisspelledKey() throws IOException {
        Files.writeString(dataDir.resolve(YamlConsoleConfigStore.FILE_NAME), """
                connectors:
                  - id: alpha-loki
                    knd: loki
                """);

        assertThatThrownBy(() -> new YamlConsoleConfigStore(dataDir).load())
                .isInstanceOf(ConsoleConfigStoreException.class)
                .hasMessageContaining(YamlConsoleConfigStore.FILE_NAME)
                .hasMessageContaining("knd");
    }
}
