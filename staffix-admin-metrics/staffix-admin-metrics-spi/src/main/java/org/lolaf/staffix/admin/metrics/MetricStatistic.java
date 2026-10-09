package org.lolaf.staffix.admin.metrics;

/**
 * What a query reads of a meter. Timers are read in seconds, rates per second.
 */
public enum MetricStatistic {
    /**
     * Recordings per second, from a timer's count.
     */
    RATE,
    /**
     * A timer's {@link MetricQuery#getQuantile() quantile}, from its histogram.
     */
    QUANTILE,
    /**
     * A timer's mean, its sum over its count.
     */
    MEAN,
    /**
     * A gauge's value.
     */
    VALUE,
    /**
     * The share, 0 to 1, of each step a gauge held {@link MetricQuery#getValue()}. The shares of every value add up to
     * less than 1 where samples are missing.
     */
    SHARE
}
