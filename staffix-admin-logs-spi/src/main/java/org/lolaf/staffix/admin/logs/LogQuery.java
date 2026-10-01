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
