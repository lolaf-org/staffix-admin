package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetrySourcesTest {

    private static final Emitters INITIATORS = new Emitters("otlp", "otlp-metrics");
    private static final Emitters CLIENTS = new Emitters("clients-otlp", null);
    private static final LogsSource EMITTERS_LOGS = query -> List.of();
    private static final LogsSource ALL_LOGS = query -> List.of();

    private final EngineGateway gateway = mock(EngineGateway.class);
    private final EngineTelemetry telemetry = mock(EngineTelemetry.class);
    private final SteppingClock clock = new SteppingClock();
    private final TelemetrySources sources = new TelemetrySources(gateway, telemetry, clock);

    private static SessionSummary session(String id, Emitters emitters) {
        return SessionSummary.builder()
                .id(id)
                .messagesLoggerInstanceId(emitters.messagesLogger())
                .monitoringInstanceId(emitters.monitoring())
                .build();
    }

    @BeforeEach
    void setUp() {
        when(gateway.listGroups("alpha")).thenReturn(List.of(
                SessionGroup.builder().group("beta")
                        .session(session("trading", INITIATORS))
                        .session(session("drop-copy", CLIENTS))
                        .build(),
                SessionGroup.builder().group("gamma").session(session("trading", INITIATORS)).build()));
        when(telemetry.logs(any(), any())).thenReturn(List.of(EMITTERS_LOGS));
        when(telemetry.allLogs(any())).thenReturn(List.of(ALL_LOGS));
    }

    @Test
    void resolvesTheEmittersOfTheScopesSessions() {
        sources.logs("alpha", null, null);
        sources.logs("alpha", "gamma", null);
        sources.logs("alpha", "beta", "drop-copy");

        verify(telemetry).logs("alpha", Set.of(INITIATORS, CLIENTS));
        verify(telemetry).logs("alpha", Set.of(INITIATORS));
        verify(telemetry).logs("alpha", Set.of(CLIENTS));
    }

    @Test
    void asksTheEngineOnceForQueriesCloseInTime() {
        sources.logs("alpha", null, null);
        clock.step(Duration.ofSeconds(9));
        sources.logs("alpha", null, null);
        clock.step(Duration.ofSeconds(1));
        sources.logs("alpha", null, null);

        verify(gateway, times(2)).listGroups("alpha");
    }

    @Test
    void resolvesAnEngineThatStoppedAnsweringFromItsLastAnswer() {
        sources.logs("alpha", null, null);
        clock.step(Duration.ofMinutes(5));
        when(gateway.listGroups("alpha")).thenThrow(GatewayException.unknownEngine("alpha"));

        assertThat(sources.logs("alpha", "beta", "drop-copy")).containsExactly(EMITTERS_LOGS);
        verify(telemetry).logs("alpha", Set.of(CLIENTS));
    }

    @Test
    void readsEveryConnectorOfAnEngineNeverSeen() {
        when(gateway.listGroups("delta")).thenThrow(GatewayException.unknownEngine("delta"));

        assertThat(sources.logs("delta", null, null)).containsExactly(ALL_LOGS);
    }

    @Test
    void readsEveryConnectorOfTheEngineForASessionItNoLongerHas() {
        assertThat(sources.logs("alpha", "beta", "removed")).containsExactly(ALL_LOGS);
    }

    private static final class SteppingClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T08:00:00Z");

        void step(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
