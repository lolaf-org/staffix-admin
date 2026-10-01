package org.lolaf.staffix.admin.server.audit;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;

/**
 * Records every state-changing operation with its operator and outcome, on its own logger so it can be routed to a
 * dedicated, retained file.
 */
@Slf4j(topic = "staffix.admin.audit")
@Component
public class AuditLog {

    public void record(SessionRef session, String operation, Runnable action) {
        String user = currentUser();
        try {
            action.run();
            log.info("user={} engine={} group={} session={} operation=[{}] outcome=OK",
                    user, session.getEngineId(), session.getGroup(), session.getSessionId(), operation);
        } catch (RuntimeException e) {
            log.warn("user={} engine={} group={} session={} operation=[{}] outcome=FAILED reason=[{}]",
                    user, session.getEngineId(), session.getGroup(), session.getSessionId(), operation, reason(e));
            throw e;
        }
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "?" : authentication.getName();
    }

    private static String reason(RuntimeException e) {
        if (e instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null) {
            return errorResponse.getBody().getDetail();
        }
        return e.toString();
    }
}
