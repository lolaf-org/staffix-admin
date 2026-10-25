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
package org.lolaf.staffix.admin.simulator;

import org.lolaf.staffix.api.FixInitiatorTarget;
import org.lolaf.staffix.api.InstanceProvider;
import org.lolaf.staffix.api.admin.AdminApi;
import org.lolaf.staffix.api.admin.FixAcceptorSessions;
import org.lolaf.staffix.api.admin.FixInitiatorTargets;
import org.lolaf.staffix.api.admin.FixSessionComponents;
import org.lolaf.staffix.api.alerting.ProbeDefinition;
import org.lolaf.staffix.api.alerting.ProbeId;
import org.lolaf.staffix.api.alerting.ProbeScope;
import org.lolaf.staffix.api.alerting.ProbeState;
import org.lolaf.staffix.api.alerting.ProbesListener;
import org.lolaf.staffix.api.alerting.ScopedProbeState;
import org.lolaf.staffix.api.ha.HaAdmin;
import org.lolaf.staffix.api.application.FixApplicationSessionSettingDescriptor;
import org.lolaf.staffix.api.logging.FixMessagesLoggerSettings;
import org.lolaf.staffix.api.monitoring.FixMeterDescriptor;
import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringContext;
import org.lolaf.staffix.api.session.FixSession;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionProbeScope;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.plugins.FixSessionsPluginSettings;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final SimulatedMonitoring monitoring;
    private final Map<SimulatedConnection, SimulatedProbes> probes = new LinkedHashMap<>();
    private final List<ProbesListener> probesListeners = new CopyOnWriteArrayList<>();
    private long ticks;

    SimulatedEngine(String engineId, List<SimulatedConnection> connections, List<FixMessagesLoggerSettings> loggers,
                    List<FixSessionsPluginSettings<?>> plugins) {
        this.engineId = engineId;
        this.connections = new ArrayList<>(connections);
        this.loggers = List.copyOf(loggers);
        this.plugins = List.copyOf(plugins);
        this.monitoring = new SimulatedMonitoring(engineId, plugins);
    }

    /**
     * Listeners are told after the engine's lock is released, as staffix tells them from its probes thread.
     */
    void tick(Random random) {
        List<Map.Entry<ProbeScope, ProbeState>> transitions = tickSessions(random);
        transitions.forEach(transition -> probesListeners.forEach(listener ->
                listener.onAlertTransition(transition.getKey(), transition.getValue())));
    }

    private synchronized List<Map.Entry<ProbeScope, ProbeState>> tickSessions(Random random) {
        connections.forEach(connection -> connection.running().tick(random));
        long tick = ticks++;
        Instant now = Instant.now();
        List<Map.Entry<ProbeScope, ProbeState>> transitions = new ArrayList<>();
        for (SimulatedConnection connection : connections) {
            SimulatedSession running = connection.running();
            probes(connection).tick(tick, running.status(), now)
                    .forEach(state -> transitions.add(Map.entry(FixSessionProbeScope.of(running.fixSessionId()), state)));
        }
        return transitions;
    }

    private SimulatedProbes probes(SimulatedConnection connection) {
        return probes.computeIfAbsent(connection, added -> new SimulatedProbes(probes.size()));
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
            case LOGOUT_LOGON_RESET_NUM_FLAG -> {
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
    public boolean isFixSessionsSettingsStorePersistent(String instanceId) {
        reloadFixSessionsSettingsStore(instanceId);
        return false;
    }

    @Override
    public synchronized Optional<String> findFixSessionsSettingsStore(FixSessionId fixSessionId, FixSessionType fixSessionType) {
        return stored(fixSessionId, fixSessionType).map(connection -> SETTINGS_STORE);
    }

    @Override
    public synchronized void addFixSessionSettings(String storeInstanceId, FixSessionSettings settings) {
        reloadFixSessionsSettingsStore(storeInstanceId);
        requireUnusedName(settings.getFixSessionId(), null);
        String acceptor = connections.stream()
                .filter(connection -> connection.type == FixSessionType.ACCEPTOR)
                .map(connection -> connection.instanceId)
                .findFirst()
                .orElse("acceptor");
        connections.add(new SimulatedConnection(settings.getFixSessionType() == FixSessionType.ACCEPTOR ? acceptor : "initiator",
                settings.getFixSessionType(), List.of(new SimulatedSession(settings, null, false, 0, true))));
    }

    @Override
    public synchronized void updateFixSessionSettings(FixSessionId fixSessionId, FixSessionSettings settings) {
        SimulatedConnection connection = stored(fixSessionId, settings.getFixSessionType())
                .orElseThrow(() -> noStoreHolds(fixSessionId));
        if (!settings.getFixSessionId().equals(fixSessionId)) {
            if (connection.type == FixSessionType.INITIATOR) {
                throw new IllegalArgumentException("An initiator session's id is set by the initiator's targets");
            }
            requireUnusedName(settings.getFixSessionId(), fixSessionId);
        }
        connection.restartOn(settings);
    }

    @Override
    public synchronized void removeFixSessionSettings(FixSessionId fixSessionId, FixSessionType fixSessionType) {
        connections.remove(stored(fixSessionId, fixSessionType).orElseThrow(() -> noStoreHolds(fixSessionId)));
    }

    @Override
    public Collection<FixApplicationSessionSettingDescriptor> getFixApplicationSessionSettingDescriptors(FixSessionId fixSessionId) {
        return SimulationScenario.APPLICATION_SETTINGS;
    }

    private Optional<SimulatedConnection> stored(FixSessionId fixSessionId, FixSessionType fixSessionType) {
        return connections.stream()
                .filter(connection -> connection.type == fixSessionType && connection.main().fixSessionId().equals(fixSessionId))
                .findFirst();
    }

    private void requireUnusedName(FixSessionId fixSessionId, FixSessionId replacedOrNull) {
        connections.stream()
                .flatMap(connection -> connection.configs.stream())
                .map(SimulatedSession::fixSessionId)
                .filter(id -> !id.equals(replacedOrNull) && id.getQualifiedName().equals(fixSessionId.getQualifiedName()))
                .findFirst()
                .ifPresent(id -> {
                    throw new IllegalStateException("The engine already has a session " + id.getQualifiedName());
                });
    }

    private static IllegalArgumentException noStoreHolds(FixSessionId fixSessionId) {
        return new IllegalArgumentException("No store holds session " + fixSessionId);
    }

    @Override
    public synchronized List<FixSession> getManagedFixSessions() {
        return runningConfigs().stream()
                .map(session -> SimulatedFixSession.of(session, this, () -> monitoring.context(session, this)))
                .toList();
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
    public synchronized List<FixMeterDescriptor> getFixSessionMeters(FixSessionId fixSessionId) {
        SimulatedSession session = running(fixSessionId);
        return monitoring.context(session, this).map(FixSessionsMonitoringContext::getMeterDescriptors).orElse(List.of());
    }

    @Override
    public List<FixMeterDescriptor> getEngineMeters() {
        return List.of();
    }

    @Override
    public List<ProbeScope> getComponentProbeScopes() {
        return List.of();
    }

    @Override
    public synchronized List<ProbeDefinition> getProbeDefinitions(ProbeScope scope) {
        return probes(connectionRunning(scope)).definitions();
    }

    @Override
    public synchronized List<ProbeState> getProbeStates(ProbeScope scope) {
        return probes(connectionRunning(scope)).states();
    }

    @Override
    public synchronized List<ScopedProbeState> getActiveAlerts() {
        List<ScopedProbeState> active = new ArrayList<>();
        for (SimulatedConnection connection : connections) {
            for (ProbeState state : probes(connection).states()) {
                if (state.getAlert().isPresent()) {
                    active.add(new ScopedProbeState(FixSessionProbeScope.of(connection.running().fixSessionId()), state,
                            probes(connection).runbookUrl(state.getProbeName())));
                }
            }
        }
        return active;
    }

    @Override
    public synchronized boolean clearAlert(ProbeId probeId) {
        return probes(connectionRunning(probeId.getScope())).clear(probeId.getName());
    }

    @Override
    public Optional<HaAdmin> getHa() {
        return Optional.empty();
    }

    @Override
    public void addProbesListener(ProbesListener listener) {
        probesListeners.add(listener);
    }

    @Override
    public void removeProbesListener(ProbesListener listener) {
        probesListeners.remove(listener);
    }

    private SimulatedConnection connectionRunning(ProbeScope scope) {
        return connections.stream()
                .filter(connection -> FixSessionProbeScope.of(connection.running().fixSessionId()).equals(scope))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The engine evaluates no probes of " + scope));
    }

    @Override
    public FixSessionComponents getFixSessionComponents() {
        FixSessionComponents.FixSessionComponentsBuilder components = FixSessionComponents.builder()
                .applicationFactory(FixSessionComponents.ApplicationFactory.builder()
                        .instanceId(InstanceProvider.DEFAULT_INSTANCE_ID)
                        .applicationId(InstanceProvider.DEFAULT_INSTANCE_ID)
                        .applicationId("drop-copy")
                        .build())
                .messagesStore(InstanceProvider.DEFAULT_INSTANCE_ID);
        loggers.forEach(logger -> components.messagesLogger(logger.getInstanceId()));
        plugins.forEach(plugin -> components.sessionsPlugin(FixSessionComponents.SessionsPlugin.builder()
                .instanceId(plugin.getInstanceId())
                .pluginTypes(plugin.getPluginTypes())
                .build()));
        return components.build();
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
                        .mainTarget(target(connection.main()))
                        .backupTargets(connection.backups().stream().map(SimulatedEngine::target).toList())
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
