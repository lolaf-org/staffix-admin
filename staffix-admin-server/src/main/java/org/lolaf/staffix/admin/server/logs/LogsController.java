package org.lolaf.staffix.admin.server.logs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static org.lolaf.staffix.admin.server.web.NotImplemented.notImplemented;

@RestController
@Validated
public class LogsController {

    /**
     * Matching entries, newest first.
     */
    @GetMapping("/api/logs")
    public List<LogEntry> queryLogs(@RequestParam String instanceId,
                                    @RequestParam(required = false) @Nullable String sessionId,
                                    @RequestParam Instant from,
                                    @RequestParam Instant to,
                                    @RequestParam(required = false) @Nullable String text,
                                    @RequestParam(defaultValue = "500") @Min(1) @Max(5000) int limit) {
        throw notImplemented();
    }
}
