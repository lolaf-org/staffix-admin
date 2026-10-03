package org.lolaf.staffix.admin.server.connectors;

import lombok.Value;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.lolaf.staffix.admin.secrets.Secret;
import org.lolaf.staffix.admin.server.secrets.EnvSecretResolver;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.lolaf.staffix.admin.store.ConnectorsConfig;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.EngineMapping;
import org.lolaf.staffix.admin.store.StaleConnectorsException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorsTest {

    private static final Connector ALPHA_METRICS = connector("alpha-metrics", "stub-metrics", "http://alpha:9090");
    private static final Connector ALPHA_LOGS = connector("alpha-logs", "stub-logs", "http://alpha:3100");
    private static final Connector BETA_LOGS = connector("beta-logs", "stub-logs", "http://beta:3100");

    private static final String KEY = Base64.getEncoder().encodeToString("alpha-master-key-of-32-bytes-abc".getBytes(StandardCharsets.UTF_8));
    private static final String PREVIOUS_KEY = Base64.getEncoder().encodeToString("an-older-master-key-of-32-bytes!".getBytes(StandardCharsets.UTF_8));

    private final MemoryStore store = new MemoryStore();
    private final Connectors connectors = connectors(new SecretCipher(KEY, List.of(PREVIOUS_KEY)));

    private Connectors connectors(SecretCipher cipher) {
        SettingsBinder binder = new SettingsBinder(List.of(new EnvSecretResolver(Map.of("BETA_URL", "http://beta-env:3100")::get)), cipher);
        return new Connectors(store, binder, new ConnectorSecrets(cipher), List.of(new StubMetricsFactory()), List.of(new StubLogsFactory()));
    }

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
    void readsAnEmitterFromItsOwnConnectorElseItsEngineDefaultElseTheDefault() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_METRICS).connector(ALPHA_LOGS).connector(BETA_LOGS)
                .defaults(EngineConnectors.builder().metrics("alpha-metrics").logs("alpha-logs").build())
                .engine("beta", EngineMapping.builder()
                        .defaults(EngineConnectors.builder().logs("beta-logs").build())
                        .logger("clients", "alpha-logs")
                        .monitor("unread", EngineConnectors.NONE)
                        .build())
                .build());
        LogsSource alphaLogs = connectors.logsSource("alpha-logs").orElseThrow();
        LogsSource betaLogs = connectors.logsSource("beta-logs").orElseThrow();
        MetricsSource alphaMetrics = connectors.metricsSource("alpha-metrics").orElseThrow();

        assertThat(connectors.logs("beta", List.of(new Emitters("clients", null)))).containsExactly(alphaLogs);
        assertThat(connectors.logs("beta", List.of(new Emitters("otlp", null)))).containsExactly(betaLogs);
        assertThat(connectors.logs("gamma", List.of(new Emitters("otlp", null)))).containsExactly(alphaLogs);
        assertThat(connectors.metrics("beta", List.of(new Emitters("otlp", "metrics")))).containsExactly(alphaMetrics);
        assertThat(connectors.metrics("beta", List.of(new Emitters("otlp", "unread")))).isEmpty();
    }

    @Test
    void readsEachSourceOfTheEmittersOnce() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_LOGS).connector(BETA_LOGS)
                .defaults(EngineConnectors.builder().logs("alpha-logs").build())
                .engine("beta", EngineMapping.builder().logger("clients", "beta-logs").logger("audit", EngineConnectors.NONE).build())
                .build());

        assertThat(connectors.logs("beta", List.of(new Emitters("otlp", null), new Emitters("clients", null),
                new Emitters("slf4j", null), new Emitters("audit", null))))
                .containsExactly(connectors.logsSource("alpha-logs").orElseThrow(), connectors.logsSource("beta-logs").orElseThrow());
    }

    @Test
    void collectsNoMetricsOfAnUnmonitoredSession() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_METRICS)
                .defaults(EngineConnectors.builder().metrics("alpha-metrics").build())
                .build());

        assertThat(connectors.metrics("beta", List.of(new Emitters("otlp", null)))).isEmpty();
    }

    @Test
    void collectsNothingWithoutADefault() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_LOGS)
                .engine("beta", EngineMapping.builder().logger("clients", "alpha-logs").build())
                .build());

        assertThat(connectors.logs("alpha", List.of(new Emitters("clients", null)))).isEmpty();
        assertThat(connectors.logs("beta", List.of(new Emitters("otlp", null)))).isEmpty();
        assertThat(connectors.logs("beta", List.of(new Emitters("clients", null)))).hasSize(1);
    }

    @Test
    void readsEveryConnectorOfAnEngineWhoseEmittersAreUnknown() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_METRICS).connector(ALPHA_LOGS).connector(BETA_LOGS)
                .defaults(EngineConnectors.builder().logs("alpha-logs").build())
                .engine("beta", EngineMapping.builder()
                        .logger("clients", "beta-logs")
                        .logger("audit", EngineConnectors.NONE)
                        .monitor("otlp-metrics", "alpha-metrics")
                        .build())
                .build());

        assertThat(connectors.allLogs("beta"))
                .containsExactlyInAnyOrder(connectors.logsSource("alpha-logs").orElseThrow(), connectors.logsSource("beta-logs").orElseThrow());
        assertThat(connectors.allMetrics("beta")).containsExactly(connectors.metricsSource("alpha-metrics").orElseThrow());
        assertThat(connectors.allLogs("gamma")).containsExactly(connectors.logsSource("alpha-logs").orElseThrow());
        assertThat(connectors.allMetrics("gamma")).isEmpty();
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
                .engine("alpha", EngineMapping.builder().defaults(EngineConnectors.builder().metrics("missing").logs(EngineConnectors.NONE).build())
                        .logger("clients-otlp", "alpha-metrics")
                        .logger("otlp", EngineConnectors.NONE)
                        .monitor("otlp-metrics", "missing")
                        .build())
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
                        "engine alpha: metrics connector missing is not a metrics connector",
                        "engine alpha logger clients-otlp: logs connector alpha-metrics is not a logs connector",
                        "engine alpha monitor otlp-metrics: metrics connector missing is not a metrics connector"));
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
    void storesATypedSecretEncryptedAndBuildsTheSourceWithIt() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_LOGS.toBuilder().setting("tenant", "alpha-tenant").build()).build());

        assertThat((String) store.load().getConnectors().getFirst().getSettings().get("tenant")).startsWith(SecretCipher.PREFIX);
        assertThat(connectors.logsSource("alpha-logs")).get().extracting(source -> ((StubLogsSource) source).getTenant())
                .isEqualTo("alpha-tenant");
    }

    @Test
    void keepsASecretReferenceAsWritten() {
        connectors.save(ConnectorsConfig.builder().connector(ALPHA_LOGS.toBuilder().setting("tenant", "${env:BETA_URL}").build()).build());

        assertThat(store.load().getConnectors().getFirst().getSettings()).containsEntry("tenant", "${env:BETA_URL}");
    }

    @Test
    void refusesATypedSecretWithoutAMasterKey() {
        Connectors withoutKey = connectors(new SecretCipher(null, List.of()));

        assertThatThrownBy(() -> withoutKey.save(ConnectorsConfig.builder()
                .connector(ALPHA_LOGS.toBuilder().setting("tenant", "alpha-tenant").build())
                .build()))
                .isInstanceOfSatisfying(InvalidConnectorsException.class, e -> assertThat(e.getProblems()).containsExactly(
                        "connector alpha-logs: secret tenant must be a reference such as ${env:NAME}, no master key is configured to encrypt it"));
    }

    @Test
    void encryptsASecretUnderAPreviousKeyAgainOnSave() {
        String underPreviousKey = new SecretCipher(PREVIOUS_KEY, List.of()).encrypt("alpha-tenant");

        connectors.save(ConnectorsConfig.builder().connector(ALPHA_LOGS.toBuilder().setting("tenant", underPreviousKey).build()).build());

        String stored = (String) store.load().getConnectors().getFirst().getSettings().get("tenant");
        assertThat(new SecretCipher(KEY, List.of()).decrypt(stored)).isEqualTo("alpha-tenant");
    }

    @Test
    void showsSecretsAsSetUnlessTheyAreReferences() {
        assertThat(connectors.redacted(ALPHA_LOGS.toBuilder().setting("tenant", "enc:v1:abc").build()).getSettings())
                .containsEntry("tenant", "set").containsEntry("url", "http://alpha:3100");
        assertThat(connectors.redacted(ALPHA_LOGS.toBuilder().setting("tenant", "${file:/run/secrets/tenant}").build()).getSettings())
                .containsEntry("tenant", "${file:/run/secrets/tenant}");
    }

    @Test
    void refusesTwoFactoriesOfTheSameKindAcrossRoles() {
        SecretCipher noKey = new SecretCipher(null, List.of());
        assertThatThrownBy(() -> new Connectors(store, new SettingsBinder(List.of(), noKey), new ConnectorSecrets(noKey), List.of(new StubMetricsFactory()),
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
        @Secret
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
        String tenant;

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
            return new StubLogsSource(settings.getUrl(), settings.getTenant());
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
