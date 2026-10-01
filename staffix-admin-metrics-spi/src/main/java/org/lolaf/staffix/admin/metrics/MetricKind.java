package org.lolaf.staffix.admin.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What staffix's micrometer monitoring measures. Latencies come from per message type timers, so they are read at
 * a {@link MetricQuery#getQuantile() quantile}.
 */
@Getter
@RequiredArgsConstructor
public enum MetricKind {
    /**
     * Messages per second, from the read (incoming) and write (outgoing) timers' counts.
     */
    MESSAGE_RATE(MetricUnit.PER_SECOND, true),
    READ_LATENCY(MetricUnit.SECONDS, true),
    WRITE_LATENCY(MetricUnit.SECONDS, true),
    DECODING_LATENCY(MetricUnit.SECONDS, true),
    ENCODING_LATENCY(MetricUnit.SECONDS, true),
    ROUND_TRIP_TIME(MetricUnit.SECONDS, false),
    /**
     * Remote clock minus local clock; signed.
     */
    CLOCK_OFFSET(MetricUnit.SECONDS, false);

    private final MetricUnit unit;
    private final boolean perMessageType;

    public boolean isLatency() {
        return unit == MetricUnit.SECONDS && perMessageType;
    }
}
