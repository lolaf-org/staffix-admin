package org.lolaf.staffix.admin.server.audit;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.audit.AuditEntry;
import org.lolaf.staffix.admin.audit.AuditOutcome;
import org.lolaf.staffix.admin.audit.AuditSink;

/**
 * The default sink: one line per entry on the {@code staffix.admin.audit} logger, INFO when it succeeded and WARN when
 * it failed, so the trail is as durable as the deployment's logging.
 */
@Slf4j(topic = "staffix.admin.audit")
public class LoggerAuditSink implements AuditSink {

    @Override
    public void record(AuditEntry entry) {
        String session = entry.getEngineId() == null ? ""
                : " engine=" + entry.getEngineId() + " group=" + entry.getGroup() + " session=" + entry.getSessionId();
        if (entry.getOutcome() == AuditOutcome.OK) {
            log.info("user={}{} operation=[{}] outcome=OK", entry.getUser(), session, entry.getOperation());
        } else {
            log.warn("user={}{} operation=[{}] outcome=FAILED reason=[{}]", entry.getUser(), session, entry.getOperation(), entry.getReason());
        }
    }
}
