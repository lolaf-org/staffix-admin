package org.lolaf.staffix.admin.server.connectors;

import lombok.Value;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.lolaf.staffix.admin.store.ConnectorsConfig;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.StaleConnectorsException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorsTest {

    private static final Connector ALPHA_METRICS = connector("alpha-metrics", "stub-metrics", "http://alpha:9090");
    private static final Connector ALPHA_LOGS = connector("alpha-logs", "stub-logs", "http://alpha:3100");
    private static final Connector BETA_LOGS = connector("beta-logs", "stub-logs", "http://beta:3100");

    private final MemoryStore store = new MemoryStore();
    private final Connectors connectors = new Connectors(store, new SettingsBinder(Map.of("BETA_URL", "http://beta-env:3100")::get),
            List.of(new StubMetricsFactory()), List.of(new StubLogsFactory()));

    @Test
    void buildsOneSourcePerConnectorFromItsOwnSettings() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_METRICS).connector(ALPHA_LOGS).connector(BETA_LOGS).build());

        assertThat(connectors.metricsSource("alpha-metrics")).get().extracting(source -> ((StubMetricsSource) source).getUrl())
                .isEqualTo("http://alpha:9090");
        assertThat(connectors.logsSource("alpha-logs")).get().extracting(source -> ((StubLogsSource) source).getUrl())
                .isEqualTo("http://alpha:3100");
        assertThat(connectors.logsSource("beta-logs")).get().extracting(source -> ((StubLogsSource) source).getUrl())
                .isEqualTo("http://beta:3100");
    }

    @Test
    void answersNothingForAnUnknownConnectorOrTheOtherRole() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_METRICS).connector(ALPHA_LOGS).build());

        assertThat(connectors.metricsSource("gamma")).isEmpty();
        assertThat(connectors.metricsSource("alpha-logs")).isEmpty();
        assertThat(connectors.logsSource("alpha-metrics")).isEmpty();
    }

    @Test
    void keepsASourceUntilItsConnectorChanges() {
        ConnectorsConfig saved = connectors.save(ConnectorsConfig.builder().connector(ALPHA_LOGS).connector(BETA_LOGS).build());
        LogsSource alpha = connectors.logsSource("alpha-logs").orElseThrow();
        LogsSource beta = connectors.logsSource("beta-logs").orElseThrow();

        connectors.save(saved.toBuilder().clearConnectors()
                .connector(ALPHA_LOGS)
                .connector(BETA_LOGS.toBuilder().setting("url", "${env:BETA_URL}").build())
                .build());

        assertThat(connectors.logsSource("alpha-logs")).containsSame(alpha);
        assertThat(connectors.logsSource("beta-logs")).get().isNotSameAs(beta)
                .extracting(source -> ((StubLogsSource) source).getUrl()).isEqualTo("http://beta-env:3100");
    }

    @Test
    void listsEveryProblemAndStoresNothing() {
        ConnectorsConfig invalid = ConnectorsConfig.builder()
                .connector(ALPHA_METRICS)
                .connector(ALPHA_METRICS.toBuilder().name("Again").build())
                .connector(Connector.builder().id("beta").name("Beta").kind("elastic").build())
                .connector(Connector.builder().id("gamma").kind("stub-logs").setting("url", "http://gamma").setting("tenat", "x").build())
                .connector(Connector.builder().id("delta").name("Delta").kind("stub-logs").build())
                .connector(Connector.builder().name("No id").kind("stub-logs").build())
                .defaults(EngineConnectors.builder().metrics("alpha-metrics").logs("alpha-metrics").build())
                .engine("alpha", EngineConnectors.builder().metrics("missing").logs(EngineConnectors.NONE).build())
                .build();

        assertThatThrownBy(() -> connectors.save(invalid))
                .isInstanceOfSatisfying(InvalidConnectorsException.class, e -> assertThat(e.getProblems()).containsExactly(
                        "connector alpha-metrics is defined twice",
                        "connector beta: unknown kind elastic, known: stub-logs, stub-metrics",
                        "connector gamma has no name",
                        "connector gamma: unknown setting tenat",
                        "connector delta: url is required",
                        "a connector has no id",
                        "the defaults: logs connector alpha-metrics is not a logs connector",
                        "engine alpha: metrics connector missing is not a metrics connector"));
        assertThat(store.load()).isEqualTo(ConnectorsConfig.EMPTY);
    }

    @Test
    void forgetsTheSourceOfARemovedConnector() {
        ConnectorsConfig saved = connectors.save(ConnectorsConfig.builder().connector(ALPHA_LOGS).build());
        LogsSource alpha = connectors.logsSource("alpha-logs").orElseThrow();

        ConnectorsConfig removed = connectors.save(saved.toBuilder().clearConnectors().build());
        connectors.save(removed.toBuilder().connector(ALPHA_LOGS).build());

        assertThat(connectors.logsSource("alpha-logs")).get().isNotSameAs(alpha);
    }

    @Test
    void refusesTwoFactoriesOfTheSameKindAcrossRoles() {
        assertThatThrownBy(() -> new Connectors(store, new SettingsBinder(name -> null), List.of(new StubMetricsFactory()),
                List.of(new StubLogsFactory() {
                    @Override
                    public String getKind() {
                        return "stub-metrics";
                    }
                })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stub-metrics");
    }

    private static Connector connector(String id, String kind, String url) {
        return Connector.builder().id(id).name(id).kind(kind).setting("url", url).build();
    }

    @Value
    public static class StubSettings {
        String url;
        String tenant;
    }

    @Value
    static class StubMetricsSource implements MetricsSource {
        String url;

        @Override
        public List<org.lolaf.staffix.admin.metrics.MetricSeries> query(org.lolaf.staffix.admin.metrics.MetricQuery query) {
            return List.of();
        }
    }

    @Value
    static class StubLogsSource implements LogsSource {
        String url;

        @Override
        public List<org.lolaf.staffix.admin.logs.LogEntry> query(org.lolaf.staffix.admin.logs.LogQuery query) {
            return List.of();
        }
    }

    static class StubMetricsFactory implements MetricsSourceFactory<StubSettings> {

        @Override
        public String getKind() {
            return "stub-metrics";
        }

        @Override
        public Class<StubSettings> getSettingsClass() {
            return StubSettings.class;
        }

        @Override
        public MetricsSource create(StubSettings settings) {
            if (settings.getUrl() == null) {
                throw new IllegalArgumentException("url is required");
            }
            return new StubMetricsSource(settings.getUrl());
        }
    }

    static class StubLogsFactory implements LogsSourceFactory<StubSettings> {

        @Override
        public String getKind() {
            return "stub-logs";
        }

        @Override
        public Class<StubSettings> getSettingsClass() {
            return StubSettings.class;
        }

        @Override
        public LogsSource create(StubSettings settings) {
            if (settings.getUrl() == null) {
                throw new IllegalArgumentException("url is required");
            }
            return new StubLogsSource(settings.getUrl());
        }
    }

    static class MemoryStore implements ConnectorStore {

        private ConnectorsConfig stored = ConnectorsConfig.EMPTY;

        @Override
        public synchronized ConnectorsConfig load() {
            return stored;
        }

        @Override
        public synchronized ConnectorsConfig save(ConnectorsConfig config) {
            if (config.getVersion() != stored.getVersion()) {
                throw new StaleConnectorsException(config.getVersion(), stored.getVersion());
            }
            stored = config.toBuilder().version(config.getVersion() + 1).build();
            return stored;
        }
    }
}
