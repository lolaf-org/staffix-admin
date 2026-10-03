package org.lolaf.staffix.admin.metrics;

import lombok.Value;

import java.time.Instant;

@Value
public class MetricPoint {
    Instant timestamp;
    /**
     * In the kind's {@link MetricKind#getUnit() unit}.
     */
    double value;
}
