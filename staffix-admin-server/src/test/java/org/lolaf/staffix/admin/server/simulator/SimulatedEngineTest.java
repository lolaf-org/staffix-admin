package org.lolaf.staffix.admin.server.simulator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
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

    private static final SessionRef CLIENT_TRADING = SessionRef.of("test", "client", "trading");
    private static final SessionRef CLIENT_DROP_COPY = SessionRef.of("test", "client", "drop-copy");
    private static final SessionRef LP_TRADING = SessionRef.of("test", "lp", "trading");

    private final Random random = new Random(42);
    private SimulatedEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SimulatedEngine("test", "0.9.1", List.of(
                new SimulatedConnection("clients-acceptor", List.of(config("client", "trading", SessionType.ACCEPTOR).build())),
                new SimulatedConnection("clients-acceptor", List.of(config("client", "drop-copy", SessionType.ACCEPTOR).withinSessionTime(false).build())),
                new SimulatedConnection("lp-trading", List.of(
                        config("lp", "trading", SessionType.INITIATOR).build(),
                        config("lp", "trading-drp", SessionType.INITIATOR).build()))));
        ticks(3);
    }

    @Test
    void tellsSessionsApartByGroup() {
        engine.logout(CLIENT_TRADING);

        assertThat(health(CLIENT_TRADING)).isEqualTo(SessionHealth.LOGGED_OUT_WITHIN_SESSION_TIME);
        assertThat(health(LP_TRADING)).isEqualTo(SessionHealth.LOGGED_IN);
    }

    @Test
    void groupsSessionsWithTheirInstance() {
        assertThat(engine.listGroups()).extracting(g -> g.getGroup()).containsExactly("client", "lp");
        SessionSummary lpTrading = engine.listGroups().get(1).getSessions().getFirst();
        assertThat(lpTrading.getInstanceId()).isEqualTo("lp-trading");
        assertThat(lpTrading.getConfigIds()).containsExactly("trading", "trading-drp");
        assertThat(lpTrading.getRunningConfigId()).isEqualTo("trading");
    }

    @Test
    void logsOnSessionsWithinTheirSessionTime() {
        assertThat(health(CLIENT_TRADING)).isEqualTo(SessionHealth.LOGGED_IN);
        assertThat(health(CLIENT_DROP_COPY)).isEqualTo(SessionHealth.LOGGED_OUT_OUTSIDE_SESSION_TIME);
    }

    @Test
    void staysLoggedOutAfterAnOperatorLogout() {
        engine.logout(CLIENT_TRADING);
        ticks(5);

        assertThat(engine.getSession(CLIENT_TRADING).getSummary().getDesiredState()).isEqualTo(SessionState.LOGGED_OUT);
        assertThat(health(CLIENT_TRADING)).isEqualTo(SessionHealth.LOGGED_OUT_WITHIN_SESSION_TIME);

        engine.logon(CLIENT_TRADING);
        ticks(1);
        assertThat(health(CLIENT_TRADING)).isEqualTo(SessionHealth.LOGGED_IN);
    }

    @Test
    void switchesAnInitiatorToABackupWithItsOwnSequenceNumbers() {
        long mainOutgoing = engine.getSession(LP_TRADING).getOutgoingSeqNum();

        engine.activate(LP_TRADING, "trading-drp");

        SessionSummary summary = engine.getSession(LP_TRADING).getSummary();
        assertThat(summary.getId()).isEqualTo("trading");
        assertThat(summary.getRunningConfigId()).isEqualTo("trading-drp");
        assertThat(summary.getFixSessionId()).startsWith("trading-drp:");
        assertThat(engine.getSession(LP_TRADING).getOutgoingSeqNum()).isEqualTo(1);

        engine.activate(LP_TRADING, "trading");
        assertThat(engine.getSession(LP_TRADING).getOutgoingSeqNum()).isEqualTo(mainOutgoing);
    }

    @Test
    void refusesAConfigTheSessionDoesNotHave() {
        assertThatThrownBy(() -> engine.activate(LP_TRADING, "nope"))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void refusesToSwitchAnAcceptor() {
        assertThatThrownBy(() -> engine.activate(CLIENT_TRADING, "trading"))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void refusesLogoutLogonResetOnAnAcceptor() {
        assertThatThrownBy(() -> engine.reset(CLIENT_TRADING, ResetMode.LOGOUT_LOGON_REST_NUM_FLAG))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void resetsInSessionToTwo() {
        engine.reset(CLIENT_TRADING, ResetMode.RESET_SEQUENCE_IN_SESSION);

        assertThat(engine.getSession(CLIENT_TRADING).getIncomingSeqNum()).isEqualTo(2);
        assertThat(engine.getSession(CLIENT_TRADING).getOutgoingSeqNum()).isEqualTo(2);
    }

    @Test
    void sendsAMessageWithACustomSeparator() {
        long outgoing = engine.getSession(CLIENT_TRADING).getOutgoingSeqNum();

        engine.sendMessage(CLIENT_TRADING, "35=D|11=ORDER-1|55=AAPL", '|', false);

        assertThat(engine.getSession(CLIENT_TRADING).getOutgoingSeqNum()).isEqualTo(outgoing + 1);
    }

    @Test
    void refusesAPossDupMessageWithoutSendingTime() {
        assertThatThrownBy(() -> engine.sendMessage(CLIENT_TRADING, "35=D|11=ORDER-1", '|', true))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void refusesToSendOnALoggedOutSession() {
        assertThatThrownBy(() -> engine.sendMessage(CLIENT_DROP_COPY, "35=D", '|', false))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void reportsAnUnknownSession() {
        assertThatThrownBy(() -> engine.getSession(SessionRef.of("test", "lp", "drop-copy")))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void scenarioCoversEveryHealthAndABackup() {
        List<SimulatedEngine> engines = new SimulationScenario(JsonMapper.builder().build()).engines();
        engines.forEach(e -> {
            for (int i = 0; i < 3; i++) {
                e.tick(random);
            }
        });
        List<SessionSummary> sessions = engines.stream()
                .flatMap(e -> e.listGroups().stream())
                .flatMap(g -> g.getSessions().stream())
                .toList();

        assertThat(sessions).extracting(SessionSummary::getHealth).contains(SessionHealth.values());
        assertThat(sessions).anySatisfy(s -> assertThat(s.getConfigIds()).hasSizeGreaterThan(1));
    }

    private SimulatedSession.SimulatedSessionBuilder config(String group, String id, SessionType type) {
        return SimulatedSession.builder()
                .id(id)
                .group(group)
                .fixSessionId(id + ":FIX.4.4:US->" + group.toUpperCase())
                .type(type)
                .withinSessionTime(true)
                .settings(JsonMapper.builder().build().createObjectNode());
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) {
            engine.tick(random);
        }
    }

    private SessionHealth health(SessionRef session) {
        return engine.getSession(session).getSummary().getHealth();
    }
}
