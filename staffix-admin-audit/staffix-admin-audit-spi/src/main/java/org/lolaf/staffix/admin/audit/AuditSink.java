package org.lolaf.staffix.admin.audit;

/**
 * Where the console records audit entries; every sink bean receives each entry, and the console refuses to start
 * without one. {@code staffix-admin-audit-log} logs them on {@code staffix.admin.audit}.
 */
public interface AuditSink {

    /**
     * Called after the action, on the request's thread. A failure is logged and does not undo the action, which
     * already happened; a sink that must not lose entries buffers or retries itself.
     */
    void record(AuditEntry entry);
}
