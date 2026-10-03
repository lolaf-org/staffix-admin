package org.lolaf.staffix.admin.server.telemetry;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.lolaf.staffix.admin.server.logs.LogsController;
import org.lolaf.staffix.admin.server.metrics.MetricsController;
import org.springframework.web.ErrorResponseException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryResolutionTest {

    private static final Instant TO = Instant.parse("2026-10-02T08:00:00Z");
    private static final Instant FROM = TO.minusSeconds(3600);

    private final TelemetrySources sources = mock(TelemetrySources.class);
    private final MetricsController metricsController = new MetricsController(sources);
    private final LogsController logsController = new LogsController(sources);

    private static LogEntry entry(int secondsBeforeTo) {
        return LogEntry.builder().timestamp(TO.minusSeconds(secondsBeforeTo)).line("line " + secondsBeforeTo).build();
    }

    private static MetricSeries series(String messageType, double... values) {
        MetricSeries.MetricSeriesBuilder series = MetricSeries.builder().label(MetricSeries.MESSAGE_TYPE, messageType);
        for (int i = 0; i < values.length; i++) {
            series.point(new MetricPoint(FROM.plusSeconds(60L * i), values[i]));
        }
        return series.build();
    }

    @Test
    void saysAScopeWithoutConnectorIsNotCollected() {
        when(sources.metrics("alpha", null, null)).thenReturn(List.of());
        when(sources.logs("alpha", "beta", "trading")).thenReturn(List.of());

        assertThatThrownBy(() -> metricsController.queryMetrics("alpha", null, null, MetricKind.MESSAGE_RATE, 0.99, FROM, TO))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getBody().getDetail())
                            .isEqualTo("The metrics of engine alpha are not collected: no metrics connector is assigned to its emitters");
                });
        assertThatThrownBy(() -> logsController.queryLogs("alpha", "beta", "trading", FROM, TO, null, List.of(), List.of(), 10))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getBody().getDetail()).startsWith("The logs of session beta.trading of engine alpha are not collected");
                });
    }

    @Test
    void reportsAConnectorWhoseStoredSettingsAreInvalid() {
        when(sources.logs("alpha", null, null)).thenThrow(new IllegalArgumentException("unknown setting tenat"));

        assertThatThrownBy(() -> logsController.queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), 10))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(502);
                    assertThat(e.getBody().getDetail()).isEqualTo("The logs connector is invalid: unknown setting tenat");
                });
    }

    @Test
    void mergesTheLogsOfEverySourceNewestFirstUpToTheLimit() {
        LogsSource initiators = query -> List.of(entry(10), entry(30), entry(50));
        LogsSource clients = query -> List.of(entry(20), entry(40));
        when(sources.logs("alpha", null, null)).thenReturn(List.of(initiators, clients));

        assertThat(logsController.queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), 4))
                .extracting(LogEntry::getLine).containsExactly("line 10", "line 20", "line 30", "line 40");
    }

    @Test
    void addsUpTheRatesOfTheSameMessageTypeAcrossSources() {
        MetricsSource initiators = query -> List.of(series("D", 1, 2), series("8", 5));
        MetricsSource clients = query -> List.of(series("D", 10, 20));
        when(sources.metrics("alpha", null, null)).thenReturn(List.of(initiators, clients));

        List<MetricSeries> merged = metricsController.queryMetrics("alpha", null, null, MetricKind.MESSAGE_RATE, 0.99, FROM, TO);

        assertThat(merged).containsExactly(series("D", 11, 22), series("8", 5));
    }

    @Test
    void keepsTheHighestQuantileOfTheSameMessageTypeAcrossSources() {
        MetricsSource initiators = query -> List.of(series("D", 0.002, 0.009));
        MetricsSource clients = query -> List.of(series("D", 0.005, 0.001));
        when(sources.metrics("alpha", null, null)).thenReturn(List.of(initiators, clients));

        assertThat(metricsController.queryMetrics("alpha", null, null, MetricKind.READ_LATENCY, 0.99, FROM, TO))
                .containsExactly(series("D", 0.005, 0.009));
    }
}
