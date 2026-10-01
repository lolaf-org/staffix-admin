package org.lolaf.staffix.admin.logs;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Value
@Builder
public class LogEntry {
    Instant timestamp;
    @Nullable
    String sessionId;
    @Nullable
    LogType type;
    /**
     * A FIX message with {@code |} between fields, or an event's text.
     */
    String line;
}
