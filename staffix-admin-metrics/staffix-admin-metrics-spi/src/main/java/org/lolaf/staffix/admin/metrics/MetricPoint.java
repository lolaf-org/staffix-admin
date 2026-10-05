package org.lolaf.staffix.admin.metrics;

import lombok.Value;

import java.time.Instant;

@Value
public class MetricPoint {
    Instant timestamp;
    /**
     * Per second for a rate, in seconds for a timer or a gauge with a unit.
     */
    double value;
}
