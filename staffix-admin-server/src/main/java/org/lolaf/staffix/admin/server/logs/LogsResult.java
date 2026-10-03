package org.lolaf.staffix.admin.server.logs;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.lolaf.staffix.admin.logs.LogEntry;

import java.util.List;

@Value
@Builder
public class LogsResult {
    /**
     * Newest first.
     */
    @Singular
    List<LogEntry> entries;
    /**
     * Sessions of the scope, as {@code group.session}, whose logs no connector collects; the entries leave them out.
     */
    @Singular("notCollected")
    List<String> notCollected;
}
