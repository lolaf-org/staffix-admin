package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The connector of each role: a connector id, {@link #NONE} when that telemetry is not collected, or null to fall
 * back on the next level.
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
