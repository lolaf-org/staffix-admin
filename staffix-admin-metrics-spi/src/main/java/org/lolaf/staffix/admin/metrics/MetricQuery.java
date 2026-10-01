package org.lolaf.staffix.admin.metrics;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

@Value
@Builder
public class MetricQuery {
    String instanceId;
    /**
     * Null for every session of the engine.
     */
    @Nullable
    String sessionId;
    MetricKind kind;
    /**
     * For latencies, e.g. 0.99; ignored by the other kinds.
     */
    @Nullable
    Double quantile;
    Instant from;
    Instant to;
    Duration step;
}
