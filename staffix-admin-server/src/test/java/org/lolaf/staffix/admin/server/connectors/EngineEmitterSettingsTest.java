package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineEmitterSettingsTest {

    private static final String EMITTERS = """
            {"messagesLoggers":[
              {"type":"OtlpMessagesLoggerSettings","instanceId":"otlp","otlpEndpointUrl":"http://collector:4318","logIncoming":true},
              {"type":"AsyncMessagesLoggerSettings","instanceId":"clients","logByteBufferSize":2048,
               "wrappedLoggerSettings":{"type":"OtlpMessagesLoggerSettings","instanceId":"default","otlpEndpointUrl":"http://clients:4318"}},
              {"type":"AsyncMessagesLoggerSettings","instanceId":"audit",
               "wrappedLoggerSettings":{"type":"FileMessagesLoggerSettings","instanceId":"default","logDirectory":"/var/log/fix"}},
              {"type":"DemuxMessagesLoggerSettings","instanceId":"fanout"}],
             "sessionsPlugins":[
              {"type":"ThrottlingFixSessionsPluginSettings","instanceId":"metrics","pluginTypes":["ThrottlingFixSessionsPlugin"]},
              {"type":"AsyncFixSessionsPluginSettings","instanceId":"metrics","queueSize":1024,
               "delegateSettings":{"type":"OtlpMicrometerMonitoringManagerSettings","instanceId":"metrics","baseTimeUnit":"MICROSECONDS","step":"PT5S",
                 "otlpEndpointUrl":"http://collector:4318","readLatencyEnabled":true,"writeLatencyEnabled":false,
                 "decodingLatencyEnabled":true,"encodingLatencyEnabled":false,"rttLatencyEnabled":false,"clockOffsetEnabled":false},
               "pluginTypes":["AsyncFixSessionsPlugin","FixSessionsMonitoringManager"]},
              {"type":"CustomMonitoringSettings","instanceId":"custom","pluginTypes":["FixSessionsMonitoringManager"]}]}
            """;

    private final EngineGateway gateway = mock(EngineGateway.class);
    private final EngineEmitterSettings settings = new EngineEmitterSettings(gateway, Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneOffset.UTC));

    EngineEmitterSettingsTest() {
        when(gateway.getEmitters("alpha")).thenReturn(JsonMapper.builder().build().readTree(EMITTERS));
        when(gateway.getEmitters("beta")).thenThrow(GatewayException.unknownEngine("beta"));
    }

    @Test
    void describesALoggerWithWhatItWrapsAndWhereItSends() {
        assertThat(settings.logger("alpha", "otlp")).contains(
                new EngineEmitterSettings.Logger(List.of("OtlpMessagesLoggerSettings"), "http://collector:4318", true));
        assertThat(settings.logger("alpha", "clients")).contains(new EngineEmitterSettings.Logger(
                List.of("AsyncMessagesLoggerSettings", "OtlpMessagesLoggerSettings"), "http://clients:4318", true));
    }

    @Test
    void tellsALoggerThatExportsNothingFromOneItCannotSeeInto() {
        assertThat(settings.logger("alpha", "audit")).get().extracting(EngineEmitterSettings.Logger::exports).isEqualTo(false);
        assertThat(settings.logger("alpha", "fanout")).get().extracting(EngineEmitterSettings.Logger::exports).isEqualTo(true);
    }

    @Test
    void findsTheMonitoringPluginBehindItsWrapperAmongPluginsSharingItsId() {
        EngineEmitterSettings.Monitor monitor = settings.monitor("alpha", "metrics").orElseThrow();

        assertThat(monitor.types()).containsExactly("AsyncFixSessionsPluginSettings", "OtlpMicrometerMonitoringManagerSettings");
        assertThat(monitor.timerBaseUnit()).isEqualTo(TimeUnit.MICROSECONDS);
        assertThat(monitor.otlpEndpoint()).isEqualTo("http://collector:4318");
        assertThat(monitor.exportStep()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void knowsNothingMoreOfAMonitoringPluginThatDoesNotSay() {
        EngineEmitterSettings.Monitor monitor = settings.monitor("alpha", "custom").orElseThrow();

        assertThat(monitor.timerBaseUnit()).isNull();
        assertThat(monitor.exportStep()).isNull();
    }

    @Test
    void knowsNothingOfAnEngineThatNeverAnswered() {
        assertThat(settings.logger("beta", "otlp")).isEmpty();
        assertThat(settings.monitor("alpha", "absent")).isEmpty();
    }
}
