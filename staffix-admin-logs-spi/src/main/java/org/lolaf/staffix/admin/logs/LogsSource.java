package org.lolaf.staffix.admin.logs;

import java.util.List;

/**
 * A logs backend (Loki, ...) holding what staffix's messages loggers export: every message in and out, and session
 * events. Entries are selected by the attributes staffix's OTLP logger sets: {@code fix.iid}, {@code fix.sg},
 * {@code fix.sn} (session name) and {@code fix.log.type}.
 */
public interface LogsSource {

    /**
     * Newest first, at most {@link LogQuery#getLimit()} entries.
     *
     * @throws LogsSourceException if the backend cannot be queried
     */
    List<LogEntry> query(LogQuery query);
}
