package org.lolaf.staffix.admin.metrics;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

@Value
@Builder
public class MetricQuery {
    String engineId;
    /**
     * Null for every group of the engine.
     */
    @Nullable
    String group;
    /**
     * A config id within {@link #getGroup()}, which it requires (ids are unique per group only); null for every
     * session of the group.
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
