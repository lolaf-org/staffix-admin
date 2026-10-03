package org.lolaf.staffix.admin.audit;

/**
 * Where the console records audit entries. Without a sink bean the console logs them on {@code staffix.admin.audit};
 * with one, define the logging one ({@code LoggerAuditSink}) as a bean too to keep both.
 */
public interface AuditSink {

    /**
     * Called after the action, on the request's thread. A failure is logged and does not undo the action, which
     * already happened; a sink that must not lose entries buffers or retries itself.
     */
    void record(AuditEntry entry);
}
