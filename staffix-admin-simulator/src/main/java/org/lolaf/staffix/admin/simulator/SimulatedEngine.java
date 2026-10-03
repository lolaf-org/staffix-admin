package org.lolaf.staffix.admin.simulator;

import org.lolaf.staffix.api.FixInitiatorTarget;
import org.lolaf.staffix.api.admin.AdminApi;
import org.lolaf.staffix.api.admin.FixAcceptorSessions;
import org.lolaf.staffix.api.admin.FixInitiatorTargets;
import org.lolaf.staffix.api.logging.FixMessagesLoggerSettings;
import org.lolaf.staffix.api.session.FixSession;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.plugins.FixSessionsPluginSettings;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * An engine following staffix's {@link AdminApi} rules, refusing what staffix refuses (an
 * {@link IllegalArgumentException} for a wrong request, an {@link IllegalStateException} for a state forbidding it),
 * so the console meets the same answers as with real engines.
 */
final class SimulatedEngine implements AdminApi {

    private static final String SETTINGS_STORE = "simulated";

    private final String engineId;
    private final List<SimulatedConnection> connections;
    private final List<FixMessagesLoggerSettings> loggers;
    private final List<FixSessionsPluginSettings<?>> plugins;

    SimulatedEngine(String engineId, List<SimulatedConnection> connections, List<FixMessagesLoggerSettings> loggers,
                    List<FixSessionsPluginSettings<?>> plugins) {
        this.engineId = engineId;
        this.connections = List.copyOf(connections);
        this.loggers = List.copyOf(loggers);
        this.plugins = List.copyOf(plugins);
    }

    synchronized void tick(Random random) {
        connections.forEach(connection -> connection.running().tick(random));
    }

    /**
     * The configs that run, which are the ones exchanging traffic.
     */
    synchronized List<SimulatedSession> runningConfigs() {
        return connections.stream().map(SimulatedConnection::running).toList();
    }

    synchronized boolean hasConfig(String group, String configId) {
        return connections.stream().flatMap(connection -> connection.configs.stream())
                .anyMatch(config -> config.group().equals(group) && config.name().equals(configId));
    }

    @Override
    public String getInstanceId() {
        return engineId;
    }

    @Override
    public synchronized void logonSession(FixSessionId fixSessionId) {
        running(fixSessionId).logon();
    }

    @Override
    public synchronized void logoutSession(FixSessionId fixSessionId) {
        running(fixSessionId).logout();
    }

    @Override
    public synchronized void resetSession(FixSessionId fixSessionId, ResetFixSessionMode resetFixSessionMode) {
        SimulatedSession session = running(fixSessionId);
        switch (resetFixSessionMode) {
            case LOGOUT_LOGON_REST_NUM_FLAG -> {
                if (session.settings.getFixSessionType() != FixSessionType.INITIATOR) {
                    throw new IllegalStateException(resetFixSessionMode
                            + " needs an initiator session: only the initiator sends the Logon carrying the reset");
                }
                session.resetSeqNums(1);
                session.drop(2);
            }
            case RESET_SEQUENCE -> session.resetSeqNums(1);
            case RESET_SEQUENCE_IN_SESSION -> {
                requireLoggedIn(session, resetFixSessionMode.name());
                session.resetSeqNums(2);
            }
        }
    }

    @Override
    public synchronized void setIncomingSeqNum(FixSessionId fixSessionId, long incomingSeqNum) {
        running(fixSessionId).incomingSeqNum = incomingSeqNum;
    }

    @Override
    public synchronized void setOutgoingSeqNum(FixSessionId fixSessionId, long outgoingSeqNum) {
        running(fixSessionId).outgoingSeqNum = outgoingSeqNum;
    }

    @Override
    public synchronized long getIncomingSeqNum(FixSessionId fixSessionId) {
        return running(fixSessionId).incomingSeqNum;
    }

    @Override
    public synchronized long getOutgoingSeqNum(FixSessionId fixSessionId) {
        return running(fixSessionId).outgoingSeqNum;
    }

    @Override
    public synchronized void sendFixMessage(FixSessionId fixSessionId, String fixMessage, char separator, boolean possDupFlag) {
        SimulatedSession session = running(fixSessionId);
        requireLoggedIn(session, "Sending");
        List<String> tags = Stream.of(fixMessage.split(Pattern.quote(String.valueOf(separator))))
                .map(field -> field.contains("=") ? field.substring(0, field.indexOf('=')) : "")
                .toList();
        if (!tags.contains("35")) {
            throw new IllegalArgumentException("The message has no MsgType(35) field, separated by '" + separator + "'");
        }
        if (possDupFlag && !tags.contains("52")) {
            throw new IllegalArgumentException("A PossDup message needs a SendingTime(52) to become its OrigSendingTime(122)");
        }
        session.outgoingSeqNum++;
    }

    @Override
    public synchronized List<FixSessionSettings> getManagedFixSessionsSettings() {
        return runningConfigs().stream().map(session -> session.settings).toList();
    }

    @Override
    public List<String> getFixSessionsSettingsStoresInstanceIds() {
        return List.of(SETTINGS_STORE);
    }

    @Override
    public void reloadFixSessionsSettingsStore(String instanceId) {
        if (!SETTINGS_STORE.equals(instanceId)) {
            throw new IllegalArgumentException("Unknown settings store '" + instanceId + "', known: " + SETTINGS_STORE);
        }
    }

    @Override
    public synchronized List<FixSession> getManagedFixSessions() {
        return runningConfigs().stream().map(session -> SimulatedFixSession.of(session, this)).toList();
    }

    @Override
    public List<FixMessagesLoggerSettings> getFixMessagesLoggersSettings() {
        return loggers;
    }

    @Override
    public List<FixSessionsPluginSettings<?>> getFixSessionsPluginsSettings() {
        return plugins;
    }

    @Override
    public synchronized void switchInitiatorSession(FixSessionId fixSessionId) {
        SimulatedConnection connection = connections.stream()
                .filter(c -> c.type == FixSessionType.INITIATOR && c.hasConfig(fixSessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No initiator targets FIX session " + fixSessionId));
        connection.switchTo(fixSessionId);
    }

    @Override
    public synchronized List<FixInitiatorTargets> getInitiatorsTargets() {
        return connections.stream()
                .filter(connection -> connection.type == FixSessionType.INITIATOR)
                .map(connection -> FixInitiatorTargets.builder()
                        .instanceId(connection.instanceId)
                        .activeFixSessionId(connection.running().fixSessionId())
                        .targets(connection.configs.stream().map(SimulatedEngine::target).toList())
                        .build())
                .toList();
    }

    @Override
    public synchronized List<FixAcceptorSessions> getAcceptorsSessions() {
        Map<String, FixAcceptorSessions.FixAcceptorSessionsBuilder> byAcceptor = new LinkedHashMap<>();
        connections.stream()
                .filter(connection -> connection.type == FixSessionType.ACCEPTOR)
                .forEach(connection -> byAcceptor.computeIfAbsent(connection.instanceId, id -> FixAcceptorSessions.builder().instanceId(id))
                        .fixSessionId(connection.running().fixSessionId()));
        return byAcceptor.values().stream().map(FixAcceptorSessions.FixAcceptorSessionsBuilder::build).toList();
    }

    @Override
    public void registerSessionLifecycleListener(SessionLifecycleListener listener) {
    }

    @Override
    public void unregisterSessionLifecycleListener(SessionLifecycleListener listener) {
    }

    private static FixInitiatorTarget target(SimulatedSession config) {
        String address = config.connectAddress;
        int colon = address.lastIndexOf(':');
        return FixInitiatorTarget.builder()
                .fixSessionId(config.fixSessionId())
                .connectAddress(InetSocketAddress.createUnresolved(address.substring(0, colon), Integer.parseInt(address.substring(colon + 1))))
                .build();
    }

    private SimulatedSession running(FixSessionId fixSessionId) {
        return connections.stream()
                .map(SimulatedConnection::running)
                .filter(session -> session.fixSessionId().equals(fixSessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No initiator or acceptor manages session " + fixSessionId));
    }

    private static void requireLoggedIn(SimulatedSession session, String operation) {
        if (!session.loggedIn) {
            throw new IllegalStateException(operation + " needs session '" + session.group() + "." + session.name() + "' to be logged in");
        }
    }
}
