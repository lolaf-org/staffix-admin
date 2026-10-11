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
package org.lolaf.staffix.admin.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.ringos.Deadline;
import org.lolaf.staffix.admin.http.HttpAdminApiSettings;
import org.lolaf.staffix.alerting.slf4j.Slf4jAlertsPublisherSettings;
import org.lolaf.staffix.api.FixAcceptorBuilder;
import org.lolaf.staffix.api.FixEngine;
import org.lolaf.staffix.api.FixEngineBuilder;
import org.lolaf.staffix.api.FixInitiatorBuilder;
import org.lolaf.staffix.api.FixInitiatorTarget;
import org.lolaf.staffix.api.alerting.FixAlertsPublisherSettings;
import org.lolaf.staffix.api.application.FixApplication;
import org.lolaf.staffix.api.monitoring.FixMonitoringConstants;
import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.FixSessionSettings.SessionScheduleSettings;
import org.lolaf.staffix.api.stores.FixMessagesStoreSettings;
import org.lolaf.staffix.api.version.FixApplVerID;
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
 * to the console through the HTTP admin API. Its messages are kept in memory and its alerts logged, unless it is
 * given another store or alerts publisher.
 */
@Slf4j
final class SandboxEngine {

    static final String METRICS = "metrics";

    private final String engineId;
    private final SandboxSettings settings;
    private final Map<String, Acceptor> acceptors = new LinkedHashMap<>();
    private final List<Initiator> initiators = new ArrayList<>();
    private FixMessagesStoreSettings messagesStore = MemoryMessageStoreSettings.builder().build();
    private FixAlertsPublisherSettings alertsPublisher = Slf4jAlertsPublisherSettings.builder().build();
    private FixEngine engine;

    record Session(FixSessionId fixSessionId, FixApplication application, boolean windowClosed) {

        static Session of(String group, String name, String sender, String target, FixApplication application) {
            return new Session(id(group, name, sender, target), application, false);
        }

        static Session fixt(String group, String name, String sender, String target, FixApplication application) {
            return new Session(FixSessionId.ofFIXT11(FixApplVerID.FIX50SP2, FixSessionId.FixSessionIdBuilder.builder()
                    .group(group).name(name).senderCompID(sender).targetCompID(target).build()), application, false);
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

    SandboxEngine messagesStore(FixMessagesStoreSettings store) {
        messagesStore = store;
        return this;
    }

    SandboxEngine alertsPublisher(FixAlertsPublisherSettings publisher) {
        alertsPublisher = publisher;
        return this;
    }

    void start() {
        SimpleApplicationFactorySettings.SimpleApplicationFactorySettingsBuilder applications = SimpleApplicationFactorySettings.builder();
        FixEngineBuilder.FixEngineBuilderBuilder<?, ?> builder = FixEngineBuilder.builder()
                .instanceId(engineId)
                .adminApiExporter(HttpAdminApiSettings.builder()
                        .port(settings.adminApiPort())
                        .announcement(HttpAdminApiSettings.AnnouncementSettings.builder()
                                .url(settings.consoleUrl())
                                .username(settings.consoleUsername())
                                .password(settings.consolePassword())
                                .interval(Duration.ofSeconds(10))
                                .build())
                        .build())
                .fixMessagesStore(messagesStore)
                .fixAlertsPublisher(alertsPublisher)
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
