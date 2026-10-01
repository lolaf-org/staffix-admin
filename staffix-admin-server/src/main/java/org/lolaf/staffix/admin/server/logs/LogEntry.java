package org.lolaf.staffix.admin.server.logs;

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
    String logType;
    String line;
}
