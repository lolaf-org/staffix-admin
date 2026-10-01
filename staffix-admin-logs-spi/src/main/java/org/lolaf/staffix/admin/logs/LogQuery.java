package org.lolaf.staffix.admin.logs;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

@Value
@Builder
public class LogQuery {
    String instanceId;
    /**
     * Null for every session of the engine.
     */
    @Nullable
    String sessionId;
    Instant from;
    Instant to;
    /**
     * Text the entry must contain, case sensitive as most log backends are; null for any.
     */
    @Nullable
    String text;
    /**
     * Empty for every type.
     */
    @Singular
    Set<LogType> types;
    int limit;
}
