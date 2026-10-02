package org.lolaf.staffix.admin.server.metrics;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.lolaf.staffix.admin.server.connectors.EngineTelemetry;
import org.lolaf.staffix.admin.server.web.TelemetryProblems;
import org.lolaf.staffix.admin.server.web.TimeRange;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
public class MetricsController {

    private final EngineTelemetry engineTelemetry;

    /**
     * Per message type kinds give one series per type and direction, the others one series per session.
     */
    @GetMapping("/api/metrics")
    public List<MetricSeries> queryMetrics(@RequestParam String engineId,
                                           @RequestParam(required = false) @Nullable String group,
                                           @RequestParam(required = false) @Nullable String sessionId,
                                           @RequestParam MetricKind kind,
                                           @RequestParam(defaultValue = "0.99") @DecimalMin("0") @DecimalMax("1") double quantile,
                                           @RequestParam Instant from,
                                           @RequestParam Instant to) {
        TelemetryProblems.requireGroupForSession(group, sessionId);
        MetricsSource source = source(engineId);
        TimeRange range = TimeRange.of(from, to);
        try {
            return source.query(MetricQuery.builder()
                    .engineId(engineId)
                    .group(group)
                    .sessionId(sessionId)
                    .kind(kind)
                    .quantile(kind.isLatency() ? quantile : null)
                    .from(range.getFrom())
                    .to(range.getTo())
                    .step(range.chartStep())
                    .build());
        } catch (MetricsSourceException e) {
            throw TelemetryProblems.backendFailed("metrics", e);
        }
    }

    private MetricsSource source(String engineId) {
        try {
            return engineTelemetry.metrics(engineId).orElseThrow(() -> TelemetryProblems.notCollected("metrics", engineId));
        } catch (IllegalArgumentException e) {
            throw TelemetryProblems.connectorInvalid("metrics", e);
        }
    }
}
