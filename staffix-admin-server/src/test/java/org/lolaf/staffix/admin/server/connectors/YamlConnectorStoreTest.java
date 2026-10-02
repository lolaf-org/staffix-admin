package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorStoreException;
import org.lolaf.staffix.admin.store.ConnectorsConfig;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.StaleConnectorsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlConnectorStoreTest {

    private static final ConnectorsConfig CONFIG = ConnectorsConfig.builder()
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
            .engine("gamma", EngineConnectors.builder().logs(EngineConnectors.NONE).build())
            .engine("beta", EngineConnectors.builder().metrics(EngineConnectors.NONE).build())
            .build();

    @TempDir
    Path dataDir;

    @Test
    void isEmptyBeforeTheFirstSave() {
        assertThat(new YamlConnectorStore(dataDir.resolve("absent")).load()).isEqualTo(ConnectorsConfig.EMPTY);
    }

    @Test
    void savesReadableYamlAndReadsItBack() throws IOException {
        YamlConnectorStore store = new YamlConnectorStore(dataDir.resolve("admin"));

        ConnectorsConfig saved = store.save(CONFIG);

        assertThat(saved).isEqualTo(CONFIG.toBuilder().version(1).build());
        assertThat(store.load()).isEqualTo(saved);
        assertThat(Files.readString(dataDir.resolve("admin").resolve(YamlConnectorStore.FILE_NAME))).isEqualTo("""
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
                    metrics: none
                  gamma:
                    logs: none
                """);
        assertThat(dataDir.resolve("admin")).isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    void refusesASaveBasedOnAnOlderVersion() {
        YamlConnectorStore store = new YamlConnectorStore(dataDir);
        ConnectorsConfig loadedByBoth = store.load();
        store.save(loadedByBoth.toBuilder().connector(CONFIG.getConnectors().getFirst()).build());

        assertThatThrownBy(() -> store.save(loadedByBoth.toBuilder().connector(CONFIG.getConnectors().getLast()).build()))
                .isInstanceOfSatisfying(StaleConnectorsException.class, e -> {
                    assertThat(e.getExpectedVersion()).isZero();
                    assertThat(e.getStoredVersion()).isEqualTo(1);
                });
        assertThat(store.load().getConnectors()).containsExactly(CONFIG.getConnectors().getFirst());
    }

    @Test
    void readsAHandWrittenFile() throws IOException {
        Files.writeString(dataDir.resolve(YamlConnectorStore.FILE_NAME), """
                connectors:
                  - id: alpha-loki
                    name: Alpha Loki
                    kind: loki
                    settings:
                      url: http://loki:3100
                defaults:
                  logs: alpha-loki
                """);

        ConnectorsConfig config = new YamlConnectorStore(dataDir).load();

        assertThat(config.getVersion()).isZero();
        assertThat(config.getConnectors()).extracting(Connector::getId).containsExactly("alpha-loki");
        assertThat(config.getDefaults()).isEqualTo(EngineConnectors.builder().logs("alpha-loki").build());
        assertThat(config.getEngines()).isEmpty();
    }

    @Test
    void reportsAMisspelledKey() throws IOException {
        Files.writeString(dataDir.resolve(YamlConnectorStore.FILE_NAME), """
                connectors:
                  - id: alpha-loki
                    knd: loki
                """);

        assertThatThrownBy(() -> new YamlConnectorStore(dataDir).load())
                .isInstanceOf(ConnectorStoreException.class)
                .hasMessageContaining(YamlConnectorStore.FILE_NAME)
                .hasMessageContaining("knd");
    }
}
