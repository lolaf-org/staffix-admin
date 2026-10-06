package org.lolaf.staffix.admin.server.logs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.lolaf.staffix.admin.server.web.TelemetryProblems;
import org.lolaf.staffix.admin.server.web.TimeRange;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
public class LogsController {

    private final TelemetrySources telemetrySources;

    /**
     * Matching entries, newest first.
     */
    @GetMapping("/api/logs")
    public LogsResult queryLogs(@RequestParam String engineId,
                                    @RequestParam(required = false) @Nullable String group,
                                    @RequestParam(required = false) @Nullable String sessionId,
                                    @RequestParam Instant from,
                                    @RequestParam Instant to,
                                    @RequestParam(required = false) @Nullable String text,
                                    @RequestParam(defaultValue = "") List<LogType> types,
                                    @RequestParam(defaultValue = "") List<String> msgTypes,
                                    @RequestParam(defaultValue = "500") @Min(1) @Max(5000) int limit) {
        TelemetrySources.Resolved<LogsSource> resolved = TelemetryProblems.requireSources("logs", engineId, group, sessionId,
                () -> telemetrySources.logs(engineId, group, sessionId));
        TimeRange range = TimeRange.of(from, to);
        LogQuery query = LogQuery.builder()
                .engineId(engineId)
                .group(group)
                .sessionId(sessionId)
                .from(range.getFrom())
                .to(range.getTo())
                .text(text == null || text.isBlank() ? null : text)
                .types(types)
                .msgTypes(msgTypes)
                .limit(limit)
                .build();
        try {
            return LogsResult.builder()
                    .entries(resolved.sources().stream()
                            .flatMap(source -> source.query(query).stream())
                            .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                            .limit(limit)
                            .toList())
                    .notCollected(resolved.notCollected())
                    .build();
        } catch (LogsSourceException e) {
            throw TelemetryProblems.backendFailed("logs", e);
        }
    }
}
