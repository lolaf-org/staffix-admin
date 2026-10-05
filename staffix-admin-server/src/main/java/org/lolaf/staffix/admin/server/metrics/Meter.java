package org.lolaf.staffix.admin.server.metrics;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A meter an engine's sessions publish, as the engine describes it.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class Meter {
    /**
     * As the monitoring publishes it, such as {@code messages.read.latency}.
     */
    String name;
    Type type;
    String description;
    /**
     * Tags beyond the session's own, which split the meter into several series.
     */
    @Singular
    List<String> tagKeys;
    /**
     * A gauge's unit; null for a timer or a gauge without one.
     */
    @Nullable
    TimeUnit unit;
    /**
     * Whether quantiles of this timer can be read.
     */
    boolean percentiles;
    /**
     * Registered by the session's application rather than by the monitoring.
     */
    boolean custom;

    /**
     * The same meter seen in several sessions: their tags all split it, and some sessions have its quantiles.
     */
    Meter merge(Meter other) {
        Set<String> tags = new LinkedHashSet<>(tagKeys);
        tags.addAll(other.tagKeys);
        return toBuilder().clearTagKeys().tagKeys(tags).percentiles(percentiles || other.percentiles).build();
    }

    public enum Type {
        TIMER,
        GAUGE
    }
}
