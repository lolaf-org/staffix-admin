package org.lolaf.staffix.admin.server.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.audit.AuditEntry;
import org.lolaf.staffix.admin.audit.AuditOutcome;
import org.lolaf.staffix.admin.audit.AuditSink;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogTest {

    private static final Instant NOW = Instant.parse("2026-10-03T08:00:00Z");

    private final List<AuditEntry> recorded = new CopyOnWriteArrayList<>();

    private AuditLog auditLog(AuditSink... sinks) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        for (int i = 0; i < sinks.length; i++) {
            beans.addBean("sink" + i, sinks[i]);
        }
        return new AuditLog(beans.getBeanProvider(AuditSink.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void logIn() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
    }

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsWhoDidWhatToWhichSession() {
        auditLog(recorded::add).record(SessionRef.of("alpha", "beta", "trading"), "logon", () -> { });

        assertThat(recorded).containsExactly(AuditEntry.builder().at(NOW).user("alice").operation("logon").outcome(AuditOutcome.OK)
                .engineId("alpha").group("beta").sessionId("trading").build());
    }

    @Test
    void recordsAFailureWithItsReasonAndRethrowsIt() {
        AuditLog auditLog = auditLog(recorded::add);

        assertThatThrownBy(() -> auditLog.record("connectors: save", () -> {
            throw GatewayException.invalid("bad connector");
        }, saved -> "connectors: changed")).hasMessageContaining("bad connector");

        assertThat(recorded).singleElement().satisfies(entry -> {
            assertThat(entry.getOperation()).isEqualTo("connectors: save");
            assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.FAILED);
            assertThat(entry.getReason()).isEqualTo("bad connector");
            assertThat(entry.getEngineId()).isNull();
        });
    }

    @Test
    void aFailingSinkNeitherStopsTheOthersNorTheAction() {
        AuditSink broken = entry -> {
            throw new IllegalStateException("disk full");
        };

        String saved = auditLog(broken, recorded::add).record("connectors: save", () -> "v2", result -> "connectors: " + result);

        assertThat(saved).isEqualTo("v2");
        assertThat(recorded).extracting(AuditEntry::getOperation).containsExactly("connectors: v2");
    }
}
