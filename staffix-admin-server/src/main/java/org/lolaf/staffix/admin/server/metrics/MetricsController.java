package org.lolaf.staffix.admin.server.metrics;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static org.lolaf.staffix.admin.server.web.NotImplemented.notImplemented;

@RestController
public class MetricsController {

    /**
     * One series per message type and direction.
     */
    @GetMapping("/api/metrics")
    public List<MetricSeries> queryMetrics(@RequestParam String instanceId,
                                           @RequestParam(required = false) @Nullable String sessionId,
                                           @RequestParam Instant from,
                                           @RequestParam Instant to,
                                           @RequestParam MetricKind metric) {
        throw notImplemented();
    }
}
