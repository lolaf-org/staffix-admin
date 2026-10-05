package org.lolaf.staffix.admin.simulator;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.application.FixApplicationSessionSettingDescriptor;
import org.lolaf.staffix.api.logging.FixMessagesLoggerSettings;
import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.FixSessionState;
import org.lolaf.staffix.api.session.plugins.FixSessionsPluginSettings;
import org.lolaf.staffix.api.version.FixRegularVersion;
import org.lolaf.staffix.monitoring.micrometer.otlp.OtlpMicrometerMonitoringManagerSettings;
import org.lolaf.staffix.plugins.async.AsyncFixSessionsPluginSettings;
import org.lolaf.staffix.stores.loggers.async.AsyncMessagesLoggerSettings;
import org.lolaf.staffix.stores.loggers.otlp.OtlpMessagesLoggerSettings;
import org.lolaf.staffix.stores.loggers.slf4j.Slf4jMessagesLoggerSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Three engines showing each case the console handles: initiators with backups, acceptors serving many groups,
 * sessions that drop, follow a session window or were logged out, and emitters of every shape (an OTLP logger, one
 * behind an async wrapper, one exporting nothing; monitoring plugins measuring more or less).
 */
@UtilityClass
class SimulationScenario {

    static final FixApplicationSessionSettingDescriptor LOGON_PASSWORD =
            FixApplicationSessionSettingDescriptor.secret("logon.password", "The password the session's Logon(A) carries");
    static final FixApplicationSessionSettingDescriptor ACCOUNT =
            FixApplicationSessionSettingDescriptor.of("account", "The account the session's orders are booked to");
    /**
     * What every simulated session's application declares.
     */
    static final List<FixApplicationSessionSettingDescriptor> APPLICATION_SETTINGS = List.of(LOGON_PASSWORD, ACCOUNT);

    List<SimulatedEngine> engines() {
        return List.of(paris(), london(), newYork());
    }

    private static SimulatedEngine paris() {
        EngineBuilder engine = new EngineBuilder("paris-prod", "STAFFIX-PAR");
        engine.initiator("alpha", "trading", "trading-drp");
        engine.initiator("alpha", "market-data", "market-data-drp").flaky();
        engine.initiator("alpha", "drop-copy");
        engine.initiator("beta", "trading", "trading-drp");
        engine.initiator("beta", "market-data").sessionTimeCycleTicks(45);
        engine.initiator("gamma", "trading", "trading-drp");
        engine.emitters("clients-otlp", "clients-metrics");
        engine.acceptor("gamma", "drop-copy", "dropcopy-acceptor");
        engine.acceptor("acme", "trading", "clients-acceptor");
        engine.acceptor("acme", "drop-copy", "clients-acceptor");
        engine.acceptor("globex", "trading", "clients-acceptor").loggedOut();
        engine.acceptor("globex", "drop-copy", "clients-acceptor").outsideSessionTime();
        engine.acceptor("initech", "trading", "clients-acceptor").flaky();
        engine.acceptor("initech", "drop-copy", "clients-acceptor");
        return engine.build();
    }

    private static SimulatedEngine london() {
        EngineBuilder engine = new EngineBuilder("london-prod", "STAFFIX-LDN");
        engine.initiator("delta", "trading", "trading-drp");
        engine.initiator("delta", "market-data");
        engine.initiator("epsilon", "trading", "trading-drp").flaky();
        engine.acceptor("fund-alpha", "trading", "clients-acceptor");
        engine.acceptor("fund-alpha", "drop-copy", "clients-acceptor");
        engine.acceptor("fund-beta", "trading", "clients-acceptor").sessionTimeCycleTicks(30);
        return engine.build();
    }

    private static SimulatedEngine newYork() {
        EngineBuilder engine = new EngineBuilder("nyc-uat", "STAFFIX-NYC");
        engine.emitters("slf4j", null);
        engine.acceptor("uat-client-1", "trading", "uat-acceptor");
        engine.acceptor("uat-client-2", "trading", "uat-acceptor").flaky();
        engine.acceptor("uat-client-3", "trading", "uat-acceptor").loggedOut();
        return engine.build();
    }

    private static FixMessagesLoggerSettings logger(String instanceId) {
        if (instanceId.startsWith("slf4j")) {
            return Slf4jMessagesLoggerSettings.builder().instanceId(instanceId).build();
        }
        OtlpMessagesLoggerSettings otlp = OtlpMessagesLoggerSettings.builder()
                .instanceId(instanceId)
                .otlpEndpointUrl(collector(instanceId))
                .resourceAttribute("service.name", "staffix-admin-simulator")
                .build();
        return instanceId.startsWith("clients")
                ? AsyncMessagesLoggerSettings.builder().instanceId(instanceId).wrappedLoggerSettings(otlp).build()
                : otlp;
    }

    private static FixSessionsPluginSettings<?> monitoring(String instanceId) {
        boolean clients = instanceId.startsWith("clients");
        OtlpMicrometerMonitoringManagerSettings micrometer = OtlpMicrometerMonitoringManagerSettings.builder()
                .instanceId(instanceId)
                .otlpEndpointUrl(collector(instanceId))
                .baseTimeUnit(TimeUnit.MICROSECONDS)
                .step(clients ? Duration.ofSeconds(10) : Duration.ofSeconds(1))
                .decodingLatencyEnabled(!clients)
                .encodingLatencyEnabled(!clients)
                .rttLatencyEnabled(true)
                .clockOffsetEnabled(!clients)
                .build();
        return clients ? AsyncFixSessionsPluginSettings.builder().delegateSettings(micrometer).build() : micrometer;
    }

    private static String collector(String instanceId) {
        return instanceId.startsWith("clients") ? "http://otel-clients:4318" : "http://otel-collector:4318";
    }

    private static final class EngineBuilder {

        private final String engineId;
        private final String compId;
        private final List<ConnectionSpec> connections = new ArrayList<>();
        private String messagesLogger = "otlp";
        @Nullable
        private String monitoring = "otlp-metrics";
        private int nextPort = 9001;

        EngineBuilder(String engineId, String compId) {
            this.engineId = engineId;
            this.compId = compId;
        }

        /**
         * For the sessions declared next.
         */
        void emitters(String messagesLogger, @Nullable String monitoring) {
            this.messagesLogger = messagesLogger;
            this.monitoring = monitoring;
        }

        ConnectionSpec acceptor(String group, String name, String instanceId) {
            ConnectionSpec connection = new ConnectionSpec(instanceId, FixSessionType.ACCEPTOR, group, List.of(name), messagesLogger, monitoring);
            connections.add(connection);
            return connection;
        }

        /**
         * One initiator per session, named after it; the first config is the main one, the others its backups.
         */
        ConnectionSpec initiator(String group, String... names) {
            ConnectionSpec connection = new ConnectionSpec(group + "-" + names[0], FixSessionType.INITIATOR, group, List.of(names),
                    messagesLogger, monitoring);
            connections.add(connection);
            return connection;
        }

        SimulatedEngine build() {
            Map<String, FixMessagesLoggerSettings> loggers = new LinkedHashMap<>();
            Map<String, FixSessionsPluginSettings<?>> plugins = new LinkedHashMap<>();
            List<SimulatedConnection> built = new ArrayList<>();
            for (ConnectionSpec connection : connections) {
                loggers.computeIfAbsent(connection.messagesLogger, SimulationScenario::logger);
                if (connection.monitoring != null) {
                    plugins.computeIfAbsent(connection.monitoring, SimulationScenario::monitoring);
                }
                built.add(new SimulatedConnection(connection.instanceId, connection.type, connection.names.stream()
                        .map(name -> config(connection, name))
                        .toList()));
            }
            return new SimulatedEngine(engineId, built, List.copyOf(loggers.values()), List.copyOf(plugins.values()));
        }

        private SimulatedSession config(ConnectionSpec connection, String name) {
            String counterparty = connection.group.toUpperCase();
            FixSessionSettings.FixSessionSettingsBuilder<?, ?> settings = FixSessionSettings.builder()
                    .fixSessionId(FixSessionId.of(FixRegularVersion.VERSION_44, FixSessionId.FixSessionIdBuilder.builder()
                            .group(connection.group).name(name).senderCompID(compId).targetCompID(counterparty).build()))
                    .fixSessionType(connection.type)
                    .desiredSessionState(connection.loggedOut ? FixSessionState.LOGGED_OUT : FixSessionState.LOGGED_IN)
                    .fixMessageLoggerInstanceId(connection.messagesLogger)
                    .fixApplicationSessionSetting(LOGON_PASSWORD, "demo-" + connection.group)
                    .fixApplicationSessionSetting(ACCOUNT, counterparty + "-1");
            if (connection.monitoring != null) {
                settings.fixSessionPluginsInstanceId(FixSessionsMonitoringManager.class, connection.monitoring);
            }
            String connectAddress = connection.type == FixSessionType.ACCEPTOR ? null
                    : "fix" + (name.endsWith("-drp") ? "-dr" : "") + "." + connection.group + ".example.com:" + nextPort++;
            return new SimulatedSession(settings.build(), connectAddress, connection.flaky, connection.sessionTimeCycleTicks,
                    !connection.outsideSessionTime);
        }
    }

    private static final class ConnectionSpec {
        final String instanceId;
        final FixSessionType type;
        final String group;
        final List<String> names;
        final String messagesLogger;
        @Nullable
        final String monitoring;
        boolean flaky;
        boolean loggedOut;
        boolean outsideSessionTime;
        int sessionTimeCycleTicks;

        ConnectionSpec(String instanceId, FixSessionType type, String group, List<String> names, String messagesLogger, @Nullable String monitoring) {
            this.instanceId = instanceId;
            this.type = type;
            this.group = group;
            this.names = names;
            this.messagesLogger = messagesLogger;
            this.monitoring = monitoring;
        }

        void flaky() {
            flaky = true;
        }

        void loggedOut() {
            loggedOut = true;
        }

        void outsideSessionTime() {
            outsideSessionTime = true;
        }

        void sessionTimeCycleTicks(int ticks) {
            sessionTimeCycleTicks = ticks;
        }
    }
}
