package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The connector of each role for an engine: a connector id, {@link #NONE} when the engine's telemetry is not
 * collected, or null to fall back on the default.
 */
@Value
@Builder(toBuilder = true)
public class EngineConnectors {
    public static final String NONE = "none";

    @Nullable
    String metrics;
    @Nullable
    String logs;
}
