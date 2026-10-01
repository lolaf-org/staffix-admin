package org.lolaf.staffix.admin.server.metrics;

import lombok.Value;

import java.time.Instant;

@Value
public class MetricPoint {
    Instant timestamp;
    double value;
}
