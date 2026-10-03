package org.lolaf.staffix.admin.server.audit;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;

import java.util.function.Function;
import java.util.function.Supplier;

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

    /**
     * For an action whose outcome describes it, such as what a save changed; {@code attempted} describes a failure.
     */
    public <T> T record(String attempted, Supplier<T> action, Function<T, String> done) {
        String user = currentUser();
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            log.warn("user={} operation=[{}] outcome=FAILED reason=[{}]", user, attempted, reason(e));
            throw e;
        }
        log.info("user={} operation=[{}] outcome=OK", user, done.apply(result));
        return result;
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
