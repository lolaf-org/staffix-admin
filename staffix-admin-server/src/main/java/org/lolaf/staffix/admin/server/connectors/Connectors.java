package org.lolaf.staffix.admin.server.connectors;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.lolaf.staffix.admin.store.ConnectorsConfig;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.EngineMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The connectors admins configured, each with a live source built by the factory of its kind. An engine reads a role
 * from its own connector, else from the default. A source is kept while its connector is unchanged and rebuilt when it
 * changes; the store is read on every lookup, so a hand edit of the stored connectors is picked up without a restart.
 */
public class Connectors implements EngineTelemetry {

    private final ConnectorStore store;
    private final SettingsBinder binder;
    private final Map<String, MetricsSourceFactory<?>> metricsFactories;
    private final Map<String, LogsSourceFactory<?>> logsFactories;
    private final Map<String, Built<MetricsSource>> metricsSources = new ConcurrentHashMap<>();
    private final Map<String, Built<LogsSource>> logsSources = new ConcurrentHashMap<>();

    Connectors(ConnectorStore store, SettingsBinder binder, List<MetricsSourceFactory<?>> metricsFactories,
               List<LogsSourceFactory<?>> logsFactories) {
        this.store = store;
        this.binder = binder;
        this.metricsFactories = byKind(metricsFactories, MetricsSourceFactory::getKind);
        this.logsFactories = byKind(logsFactories, LogsSourceFactory::getKind);
        Set<String> sharedKinds = new HashSet<>(this.metricsFactories.keySet());
        sharedKinds.retainAll(this.logsFactories.keySet());
        if (!sharedKinds.isEmpty()) {
            throw new IllegalStateException("Kinds with both a metrics and a logs factory: " + sharedKinds);
        }
    }

    public ConnectorsConfig load() {
        return store.load();
    }

    /**
     * @throws InvalidConnectorsException listing every problem, before anything is stored
     */
    public ConnectorsConfig save(ConnectorsConfig config) {
        List<String> problems = problems(config);
        if (!problems.isEmpty()) {
            throw new InvalidConnectorsException(problems);
        }
        ConnectorsConfig saved = store.save(config);
        Set<String> ids = saved.getConnectors().stream().map(Connector::getId).collect(Collectors.toSet());
        metricsSources.keySet().retainAll(ids);
        logsSources.keySet().retainAll(ids);
        return saved;
    }

    @Override
    public Optional<MetricsSource> metrics(String engineId) {
        ConnectorsConfig config = store.load();
        return assigned(config, engineId, EngineConnectors::getMetrics).flatMap(connectorId -> metricsSource(config, connectorId));
    }

    @Override
    public Optional<LogsSource> logs(String engineId) {
        ConnectorsConfig config = store.load();
        return assigned(config, engineId, EngineConnectors::getLogs).flatMap(connectorId -> logsSource(config, connectorId));
    }

    /**
     * Empty when no such connector exists or it does not serve metrics.
     *
     * @throws IllegalArgumentException if its stored settings cannot make a source, e.g. after a hand edit
     */
    public Optional<MetricsSource> metricsSource(String connectorId) {
        return metricsSource(store.load(), connectorId);
    }

    /**
     * Empty when no such connector exists or it does not serve logs.
     *
     * @throws IllegalArgumentException if its stored settings cannot make a source, e.g. after a hand edit
     */
    public Optional<LogsSource> logsSource(String connectorId) {
        return logsSource(store.load(), connectorId);
    }

    private Optional<MetricsSource> metricsSource(ConnectorsConfig config, String connectorId) {
        return connector(config, connectorId)
                .filter(connector -> metricsFactories.containsKey(connector.getKind()))
                .map(connector -> metricsSources.compute(connectorId, (id, built) -> built != null && built.connector().equals(connector)
                        ? built : new Built<>(connector, createMetricsSource(connector))).source());
    }

    private Optional<LogsSource> logsSource(ConnectorsConfig config, String connectorId) {
        return connector(config, connectorId)
                .filter(connector -> logsFactories.containsKey(connector.getKind()))
                .map(connector -> logsSources.compute(connectorId, (id, built) -> built != null && built.connector().equals(connector)
                        ? built : new Built<>(connector, createLogsSource(connector))).source());
    }

    private static Optional<String> assigned(ConnectorsConfig config, String engineId, Function<EngineConnectors, @Nullable String> role) {
        EngineMapping mapping = config.getEngines().get(engineId);
        String connectorId = mapping != null && role.apply(mapping.getDefaults()) != null
                ? role.apply(mapping.getDefaults()) : role.apply(config.getDefaults());
        return connectorId == null || connectorId.equals(EngineConnectors.NONE) ? Optional.empty() : Optional.of(connectorId);
    }

    private static Optional<Connector> connector(ConnectorsConfig config, String connectorId) {
        return config.getConnectors().stream().filter(connector -> connectorId.equals(connector.getId())).findFirst();
    }

    private MetricsSource createMetricsSource(Connector connector) {
        return create(metricsFactories.get(connector.getKind()), connector);
    }

    private LogsSource createLogsSource(Connector connector) {
        return create(logsFactories.get(connector.getKind()), connector);
    }

    private <S> MetricsSource create(MetricsSourceFactory<S> factory, Connector connector) {
        return factory.create(binder.bind(connector.getSettings(), factory.getSettingsClass()));
    }

    private <S> LogsSource create(LogsSourceFactory<S> factory, Connector connector) {
        return factory.create(binder.bind(connector.getSettings(), factory.getSettingsClass()));
    }

    private List<String> problems(ConnectorsConfig config) {
        List<String> problems = new ArrayList<>();
        Set<String> metricsIds = new HashSet<>();
        Set<String> logsIds = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Connector connector : config.getConnectors()) {
            if (connector.getId() == null || connector.getId().isBlank()) {
                problems.add("a connector has no id");
                continue;
            }
            String id = connector.getId();
            if (!ids.add(id)) {
                problems.add("connector " + id + " is defined twice");
            }
            if (connector.getName() == null || connector.getName().isBlank()) {
                problems.add("connector " + id + " has no name");
            }
            try {
                if (metricsFactories.containsKey(connector.getKind())) {
                    createMetricsSource(connector);
                    metricsIds.add(id);
                } else if (logsFactories.containsKey(connector.getKind())) {
                    createLogsSource(connector);
                    logsIds.add(id);
                } else {
                    problems.add("connector " + id + ": unknown kind " + connector.getKind() + ", known: " + kinds());
                }
            } catch (IllegalArgumentException e) {
                problems.add("connector " + id + ": " + e.getMessage());
            }
        }
        checkLinks("the defaults", config.getDefaults(), metricsIds, logsIds, problems);
        config.getEngines().forEach((engineId, mapping) -> {
            String owner = "engine " + engineId;
            checkLinks(owner, mapping.getDefaults(), metricsIds, logsIds, problems);
            mapping.getLoggers().forEach((logger, connectorId) ->
                    checkLink(owner + " logger " + logger, "logs", connectorId, logsIds, problems));
            mapping.getMonitors().forEach((monitor, connectorId) ->
                    checkLink(owner + " monitor " + monitor, "metrics", connectorId, metricsIds, problems));
        });
        return problems;
    }

    private static void checkLinks(String owner, EngineConnectors links, Set<String> metricsIds, Set<String> logsIds, List<String> problems) {
        checkLink(owner, "metrics", links.getMetrics(), metricsIds, problems);
        checkLink(owner, "logs", links.getLogs(), logsIds, problems);
    }

    private static void checkLink(String owner, String role, @Nullable String connectorId, Set<String> connectorIds, List<String> problems) {
        if (connectorId != null && !connectorId.equals(EngineConnectors.NONE) && !connectorIds.contains(connectorId)) {
            problems.add(owner + ": " + role + " connector " + connectorId + " is not a " + role + " connector");
        }
    }

    private String kinds() {
        Set<String> kinds = new TreeSet<>(metricsFactories.keySet());
        kinds.addAll(logsFactories.keySet());
        return String.join(", ", kinds);
    }

    private static <F> Map<String, F> byKind(List<F> factories, Function<F, String> kind) {
        return factories.stream().collect(Collectors.toUnmodifiableMap(kind, Function.identity()));
    }

    private record Built<T>(Connector connector, T source) {
    }
}
