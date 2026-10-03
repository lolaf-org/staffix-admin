package org.lolaf.staffix.admin.server.audit;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.audit.AuditEntry;
import org.lolaf.staffix.admin.audit.AuditOutcome;
import org.lolaf.staffix.admin.audit.AuditSink;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Records every change an operator makes, or tries to make, to each {@link AuditSink} bean. The console refuses to
 * start without one: changes would go unrecorded.
 */
@Slf4j
@Component
public class AuditLog {

    private final List<AuditSink> sinks;
    private final Clock clock;

    AuditLog(ObjectProvider<AuditSink> sinks, Clock clock) {
        this.sinks = sinks.orderedStream().toList();
        if (this.sinks.isEmpty()) {
            throw new IllegalStateException("No audit sink: put an AuditSink plugin, such as staffix-admin-audit-log, in plugins/");
        }
        this.clock = clock;
    }

    public void record(SessionRef session, String operation, Runnable action) {
        AuditEntry.AuditEntryBuilder entry = entry(operation)
                .engineId(session.getEngineId())
                .group(session.getGroup())
                .sessionId(session.getSessionId());
        try {
            action.run();
        } catch (RuntimeException e) {
            write(entry.outcome(AuditOutcome.FAILED).reason(reason(e)).build());
            throw e;
        }
        write(entry.outcome(AuditOutcome.OK).build());
    }

    /**
     * For an action whose outcome describes it, such as what a save changed; {@code attempted} describes a failure.
     */
    public <T> T record(String attempted, Supplier<T> action, Function<T, String> done) {
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            write(entry(attempted).outcome(AuditOutcome.FAILED).reason(reason(e)).build());
            throw e;
        }
        write(entry(done.apply(result)).outcome(AuditOutcome.OK).build());
        return result;
    }

    private AuditEntry.AuditEntryBuilder entry(String operation) {
        return AuditEntry.builder().at(clock.instant()).user(currentUser()).operation(operation);
    }

    private void write(AuditEntry entry) {
        for (AuditSink sink : sinks) {
            try {
                sink.record(entry);
            } catch (RuntimeException e) {
                log.error("Audit sink {} lost the entry of {} by {}", sink.getClass().getName(), entry.getOperation(), entry.getUser(), e);
            }
        }
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "?" : authentication.getName();
    }

    private static @Nullable String reason(RuntimeException e) {
        if (e instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null) {
            return errorResponse.getBody().getDetail();
        }
        return e.toString();
    }
}
