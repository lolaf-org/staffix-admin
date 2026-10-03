package org.lolaf.staffix.admin.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.ringos.Deadline;
import org.lolaf.staffix.admin.http.HttpAdminApiSettings;
import org.lolaf.staffix.api.FixAcceptorBuilder;
import org.lolaf.staffix.api.FixEngine;
import org.lolaf.staffix.api.FixEngineBuilder;
import org.lolaf.staffix.api.FixInitiatorBuilder;
import org.lolaf.staffix.api.FixInitiatorTarget;
import org.lolaf.staffix.api.application.FixApplication;
import org.lolaf.staffix.api.monitoring.FixMonitoringConstants;
import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.FixSessionSettings.SessionScheduleSettings;
import org.lolaf.staffix.api.version.FixRegularVersion;
import org.lolaf.staffix.application.factories.simple.SimpleApplicationFactorySettings;
import org.lolaf.staffix.monitoring.micrometer.otlp.OtlpMicrometerMonitoringManagerSettings;
import org.lolaf.staffix.stores.loggers.otlp.OtlpMessagesLoggerSettings;
import org.lolaf.staffix.stores.messages.memory.MemoryMessageStoreSettings;
import org.lolaf.staffix.stores.sessions.memory.MemorySessionsSettingsStoreSettings;

import java.net.InetSocketAddress;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One sandbox engine: its acceptors and initiators, exporting metrics and FIX logs over OTLP and announcing itself
 * to the console through the HTTP admin API.
 */
@Slf4j
final class SandboxEngine {

    static final String METRICS = "metrics";

    private final String engineId;
    private final SandboxSettings settings;
    private final Map<String, Acceptor> acceptors = new LinkedHashMap<>();
    private final List<Initiator> initiators = new ArrayList<>();
    private FixEngine engine;

    record Session(FixSessionId fixSessionId, FixApplication application, boolean windowClosed) {

        static Session of(String group, String name, String sender, String target, FixApplication application) {
            return new Session(id(group, name, sender, target), application, false);
        }

        static Session backup(String group, String name, String sender, String target) {
            return new Session(id(group, name, sender, target), null, false);
        }

        Session withWindowClosed() {
            return new Session(fixSessionId, application, true);
        }

        private static FixSessionId id(String group, String name, String sender, String target) {
            return FixSessionId.of(FixRegularVersion.VERSION_44, FixSessionId.FixSessionIdBuilder.builder()
                    .group(group).name(name).senderCompID(sender).targetCompID(target).build());
        }
    }

    /**
     * A backup's session only gives its id: it runs on its main target's settings and application.
     */
    record Target(Session session, int port) {
    }

    private record Acceptor(int port, List<Session> sessions) {
    }

    private record Initiator(String instanceId, Target main, List<Target> backups) {
    }

    SandboxEngine(String engineId, SandboxSettings settings) {
        this.engineId = engineId;
        this.settings = settings;
    }

    SandboxEngine acceptor(String instanceId, int port, Session... sessions) {
        acceptors.put(instanceId, new Acceptor(port, List.of(sessions)));
        return this;
    }

    SandboxEngine initiator(String instanceId, Target main, Target... backups) {
        initiators.add(new Initiator(instanceId, main, List.of(backups)));
        return this;
    }

    void start() {
        SimpleApplicationFactorySettings.SimpleApplicationFactorySettingsBuilder applications = SimpleApplicationFactorySettings.builder();
        FixEngineBuilder.FixEngineBuilderBuilder<?, ?> builder = FixEngineBuilder.builder()
                .instanceId(engineId)
                .adminApiExporter(HttpAdminApiSettings.builder()
                        .port(settings.adminApiPort())
                        .announceUrl(settings.consoleUrl())
                        .announceUsername(settings.consoleUsername())
                        .announcePassword(settings.consolePassword())
                        .announceInterval(Duration.ofSeconds(10))
                        .build())
                .fixMessagesStore(MemoryMessageStoreSettings.builder().build())
                .fixMessagesLogger(OtlpMessagesLoggerSettings.builder()
                        .otlpEndpointUrl(settings.otlpUrl())
                        .resourceAttribute(FixMonitoringConstants.OTLP_SERVICE_NAME, "staffix-admin-sandbox")
                        .resourceAttribute(FixMonitoringConstants.OTLP_SERVICE_INSTANCE_ID, engineId)
                        .fixMessageFieldsDelimiter('|')
                        .build())
                .fixSessionsPlugin(OtlpMicrometerMonitoringManagerSettings.builder()
                        .instanceId(METRICS)
                        .otlpEndpointUrl(settings.otlpUrl())
                        .resourceAttribute(FixMonitoringConstants.OTLP_SERVICE_NAME, "staffix-admin-sandbox")
                        .resourceAttribute(FixMonitoringConstants.OTLP_SERVICE_INSTANCE_ID, engineId)
                        .step(Duration.ofSeconds(5))
                        .baseTimeUnit(TimeUnit.MICROSECONDS)
                        .decodingLatencyEnabled(true)
                        .encodingLatencyEnabled(true)
                        .rttLatencyEnabled(true)
                        .clockOffsetEnabled(true)
                        .build());
        acceptors.forEach((instanceId, acceptor) -> {
            MemorySessionsSettingsStoreSettings.MemorySessionsSettingsStoreSettingsBuilder store =
                    MemorySessionsSettingsStoreSettings.builder().instanceId(instanceId);
            acceptor.sessions().forEach(session -> {
                applications.application(session.fixSessionId().getQualifiedName(), session.application());
                store.fixSessionSetting(settings(session, FixSessionType.ACCEPTOR));
            });
            builder.fixSessionsSettingsStore(store.build());
        });
        MemorySessionsSettingsStoreSettings.MemorySessionsSettingsStoreSettingsBuilder initiatorsStore =
                MemorySessionsSettingsStoreSettings.builder().instanceId("initiators");
        initiators.forEach(initiator -> {
            Session main = initiator.main().session();
            applications.application(main.fixSessionId().getQualifiedName(), main.application());
            initiatorsStore.fixSessionSetting(settings(main, FixSessionType.INITIATOR));
        });
        builder.fixSessionsSettingsStore(initiatorsStore.build());
        builder.fixApplicationFactory(applications.build());

        engine = builder.build().instance().start();
        acceptors.forEach((instanceId, acceptor) -> engine.newAcceptor(FixAcceptorBuilder.builder()
                .instanceId(instanceId)
                .bindAddress(new InetSocketAddress("localhost", acceptor.port()))
                .targetFixSessionsSettingsStoreInstancesIds(List.of(instanceId))
                .build()).start());
        initiators.forEach(initiator -> {
            FixInitiatorBuilder.FixInitiatorBuilderBuilder<?, ?> fixInitiator = FixInitiatorBuilder.builder()
                    .instanceId(initiator.instanceId())
                    .mainTarget(target(initiator.main()));
            initiator.backups().forEach(backup -> fixInitiator.backupTarget(target(backup)));
            engine.newInitiator(fixInitiator.build()).start();
        });
        log.info("Engine {} started", engineId);
    }

    void stop() {
        if (engine != null) {
            engine.stop(Deadline.of(Duration.ofSeconds(5)));
        }
    }

    private static FixInitiatorTarget target(Target target) {
        return FixInitiatorTarget.builder()
                .fixSessionId(target.session().fixSessionId())
                .connectAddress(new InetSocketAddress("localhost", target.port()))
                .build();
    }

    private static FixSessionSettings settings(Session session, FixSessionType type) {
        FixSessionSettings.FixSessionSettingsBuilder<?, ?> settings = FixSessionSettings.builder()
                .fixSessionId(session.fixSessionId())
                .fixSessionType(type)
                .fixApplicationInstanceId(session.fixSessionId().getQualifiedName())
                .fixSessionPluginsInstanceId(FixSessionsMonitoringManager.class, METRICS)
                .rttMeasurementSettings(FixSessionSettings.RttMeasurementSettings.builder()
                        .probeInterval(Duration.ofSeconds(2))
                        .build())
                .resetSeqNumOnLogon(true);
        if (session.windowClosed()) {
            settings.sessionScheduleSettings(openOnlyTheFirstMinuteOfToday());
        }
        return settings.build();
    }

    private static SessionScheduleSettings openOnlyTheFirstMinuteOfToday() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        return SessionScheduleSettings.builder()
                .sessionSchedule(SessionScheduleSettings.ScheduleEntry.builder()
                        .startDay(today).startTime(LocalTime.MIDNIGHT)
                        .endDay(today).endTime(LocalTime.of(0, 1))
                        .build())
                .build();
    }
}
