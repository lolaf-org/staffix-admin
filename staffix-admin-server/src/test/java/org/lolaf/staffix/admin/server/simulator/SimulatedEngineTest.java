package org.lolaf.staffix.admin.server.simulator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionHealth;
import org.lolaf.staffix.admin.server.sessions.SessionState;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedEngineTest {

    private final Random random = new Random(42);
    private SimulatedEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SimulatedEngine("test", "0.9.1",
                List.of(session("ACC", SessionType.ACCEPTOR).build(),
                        session("ACC-OUTSIDE", SessionType.ACCEPTOR).withinSessionTime(false).build()),
                List.of(new SimulatedInitiator("INI-initiator", List.of(
                        session("INI", SessionType.INITIATOR).build(),
                        session("INI-DR", SessionType.INITIATOR).build()))));
        ticks(3);
    }

    @Test
    void logsOnSessionsWithinTheirSessionTime() {
        assertThat(health("ACC")).isEqualTo(SessionHealth.LOGGED_IN);
        assertThat(health("ACC-OUTSIDE")).isEqualTo(SessionHealth.LOGGED_OUT_OUTSIDE_SESSION_TIME);
    }

    @Test
    void staysLoggedOutAfterAnOperatorLogout() {
        engine.logout("ACC");
        ticks(5);

        SessionSummary summary = engine.getSession("ACC").getSummary();
        assertThat(summary.getHealth()).isEqualTo(SessionHealth.LOGGED_OUT_WITHIN_SESSION_TIME);
        assertThat(summary.getDesiredState()).isEqualTo(SessionState.LOGGED_OUT);

        engine.logon("ACC");
        ticks(1);
        assertThat(health("ACC")).isEqualTo(SessionHealth.LOGGED_IN);
    }

    @Test
    void listsOnlyTheActiveInitiatorTarget() {
        assertThat(sessionIds()).containsExactly("ACC", "ACC-OUTSIDE", "INI");

        engine.activateInitiatorTarget("INI-DR");

        assertThat(sessionIds()).containsExactly("ACC", "ACC-OUTSIDE", "INI-DR");
        assertThat(engine.getSession("INI-DR").getInitiators().getFirst().getActiveSessionId()).isEqualTo("INI-DR");
    }

    @Test
    void refusesLogoutLogonResetOnAnAcceptor() {
        assertThatThrownBy(() -> engine.reset("ACC", ResetMode.LOGOUT_LOGON_REST_NUM_FLAG))
                .isInstanceOfSatisfying(GatewayException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void resetsInSessionToTwo() {
        engine.reset("ACC", ResetMode.RESET_SEQUENCE_IN_SESSION);

        assertThat(engine.getSession("ACC").getIncomingSeqNum()).isEqualTo(2);
        assertThat(engine.getSession("ACC").getOutgoingSeqNum()).isEqualTo(2);
    }

    @Test
    void sendsAMessageWithACustomSeparator() {
        long outgoing = engine.getSession("ACC").getOutgoingSeqNum();

        engine.sendMessage("ACC", "35=D|11=ORDER-1|55=AAPL", '|', false);

        assertThat(engine.getSession("ACC").getOutgoingSeqNum()).isEqualTo(outgoing + 1);
    }

    @Test
    void refusesAPossDupMessageWithoutSendingTime() {
        assertThatThrownBy(() -> engine.sendMessage("ACC", "35=D|11=ORDER-1", '|', true))
                .isInstanceOfSatisfying(GatewayException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void refusesToSendOnALoggedOutSession() {
        assertThatThrownBy(() -> engine.sendMessage("ACC-OUTSIDE", "35=D", '|', false))
                .isInstanceOfSatisfying(GatewayException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void reportsAnUnknownSession() {
        assertThatThrownBy(() -> engine.getSession("NOPE"))
                .isInstanceOfSatisfying(GatewayException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void scenarioCoversEveryHealth() {
        List<SimulatedEngine> engines = new SimulationScenario(JsonMapper.builder().build()).engines();
        engines.forEach(e -> {
            for (int i = 0; i < 3; i++) {
                e.tick(random);
            }
        });

        assertThat(engines.stream()
                .flatMap(e -> e.listSessions().stream())
                .flatMap(g -> g.getSessions().stream())
                .map(SessionSummary::getHealth))
                .contains(SessionHealth.values());
    }

    private SimulatedSession.SimulatedSessionBuilder session(String id, SessionType type) {
        return SimulatedSession.builder()
                .id(id)
                .group("group")
                .fixSessionId(id + ":FIX.4.4:US->THEM")
                .type(type)
                .withinSessionTime(true)
                .settings(JsonMapper.builder().build().createObjectNode());
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) {
            engine.tick(random);
        }
    }

    private SessionHealth health(String sessionId) {
        return engine.getSession(sessionId).getSummary().getHealth();
    }

    private List<String> sessionIds() {
        return engine.listSessions().stream()
                .flatMap(g -> g.getSessions().stream())
                .map(SessionSummary::getId)
                .toList();
    }
}
