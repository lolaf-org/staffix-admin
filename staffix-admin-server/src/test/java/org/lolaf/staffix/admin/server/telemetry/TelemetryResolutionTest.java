package org.lolaf.staffix.admin.server.telemetry;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.server.connectors.EngineTelemetry;
import org.lolaf.staffix.admin.server.logs.LogsController;
import org.lolaf.staffix.admin.server.metrics.MetricsController;
import org.springframework.web.ErrorResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryResolutionTest {

    private static final Instant TO = Instant.parse("2026-10-02T08:00:00Z");
    private static final Instant FROM = TO.minusSeconds(3600);

    @Test
    void saysAnEngineWithoutConnectorIsNotCollected() {
        EngineTelemetry nothing = telemetry(Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> new MetricsController(nothing).queryMetrics("alpha", null, null, MetricKind.MESSAGE_RATE, 0.99, FROM, TO))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getBody().getDetail()).isEqualTo("The metrics of engine alpha are not collected: no metrics connector is assigned to it");
                });
        assertThatThrownBy(() -> new LogsController(nothing).queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), 10))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void reportsAConnectorWhoseStoredSettingsAreInvalid() {
        EngineTelemetry invalid = new EngineTelemetry() {
            @Override
            public Optional<MetricsSource> metrics(String engineId) {
                throw new IllegalArgumentException("unknown setting tenat");
            }

            @Override
            public Optional<LogsSource> logs(String engineId) {
                throw new IllegalArgumentException("unknown setting tenat");
            }
        };

        assertThatThrownBy(() -> new LogsController(invalid).queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), 10))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(502);
                    assertThat(e.getBody().getDetail()).isEqualTo("The logs connector is invalid: unknown setting tenat");
                });
    }

    @Test
    void queriesTheEnginesSource() {
        MetricsSource metrics = query -> List.of();
        LogsSource logs = query -> List.of();
        EngineTelemetry assigned = telemetry(Optional.of(metrics), Optional.of(logs));

        assertThat(new MetricsController(assigned).queryMetrics("alpha", null, null, MetricKind.MESSAGE_RATE, 0.99, FROM, TO)).isEmpty();
        assertThat(new LogsController(assigned).queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), 10)).isEmpty();
    }

    private static EngineTelemetry telemetry(Optional<MetricsSource> metrics, Optional<LogsSource> logs) {
        return new EngineTelemetry() {
            @Override
            public Optional<MetricsSource> metrics(String engineId) {
                return metrics;
            }

            @Override
            public Optional<LogsSource> logs(String engineId) {
                return logs;
            }
        };
    }
}
