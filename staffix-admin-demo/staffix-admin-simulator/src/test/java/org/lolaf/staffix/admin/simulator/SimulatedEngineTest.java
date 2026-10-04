package org.lolaf.staffix.admin.simulator;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.api.admin.AdminApi;
import org.lolaf.staffix.api.admin.FixInitiatorTargets;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSession;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.session.FixSessionState;
import org.lolaf.staffix.api.version.FixRegularVersion;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedEngineTest {

    private final SimulatedEngine paris = SimulationScenario.engines().getFirst();
    private final Random random = new Random(42);

    private FixSession session(String group, String name) {
        return paris.getManagedFixSessions().stream()
                .filter(s -> s.getFixSessionId().getGroup().equals(group) && s.getFixSessionId().getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) {
            paris.tick(random);
        }
    }

    @Test
    void logsSessionsOnWithinTheirWindowAndKeepsAnOperatorLogout() {
        ticks(3);
        FixSessionId acme = session("acme", "trading").getFixSessionId();
        assertThat(session("acme", "trading").isLoggedIn()).isTrue();
        assertThat(session("globex", "drop-copy").isLoggedIn()).isFalse();

        paris.logoutSession(acme);
        ticks(5);

        assertThat(session("acme", "trading").isLoggedIn()).isFalse();
        assertThat(session("acme", "trading").getDesiredState().name()).isEqualTo("LOGGED_OUT");
    }

    @Test
    void refusesWhatStaffixRefuses() {
        FixSessionId acme = session("acme", "trading").getFixSessionId();

        assertThatThrownBy(() -> paris.resetSession(acme, AdminApi.ResetFixSessionMode.LOGOUT_LOGON_RESET_NUM_FLAG))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("needs an initiator session");
        ticks(3);
        assertThatThrownBy(() -> paris.sendFixMessage(acme, "8=FIX.4.4|55=VOD.L|", '|', false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MsgType(35)");
    }

    @Test
    void switchesAnInitiatorToItsBackup() {
        FixInitiatorTargets trading = paris.getInitiatorsTargets().stream()
                .filter(targets -> targets.getInstanceId().equals("alpha-trading"))
                .findFirst()
                .orElseThrow();
        FixSessionId backup = trading.getBackupTargets().get(0).getFixSessionId();

        paris.switchInitiatorSession(backup);

        assertThat(paris.getInitiatorsTargets()).filteredOn(targets -> targets.getInstanceId().equals("alpha-trading"))
                .singleElement().extracting(FixInitiatorTargets::getActiveFixSessionId).isEqualTo(backup);
        assertThat(session("alpha", "trading-drp").getFixSessionSettings().getFixSessionId()).isEqualTo(backup);
    }

    @Test
    void servesRealStaffixEmitterSettings() {
        assertThat(paris.getFixMessagesLoggersSettings()).extracting(settings -> settings.getClass().getSimpleName())
                .containsExactly("OtlpMessagesLoggerSettings", "AsyncMessagesLoggerSettings");
        assertThat(paris.getFixSessionsPluginsSettings()).extracting(settings -> List.copyOf(settings.getPluginTypes()).getLast().getSimpleName())
                .containsOnly("FixSessionsMonitoringManager");
    }

    private FixSessionSettings settingsOf(String group, String name) {
        return session(group, name).getFixSessionSettings();
    }

    @Test
    void anUpdatedSessionRestartsOnItsNewSettings() {
        ticks(3);
        FixSessionSettings acme = settingsOf("acme", "trading");

        paris.updateFixSessionSettings(acme.getFixSessionId(), acme.toBuilder().desiredSessionState(FixSessionState.LOGGED_OUT).build());

        assertThat(session("acme", "trading").isLoggedIn()).isFalse();
        assertThat(settingsOf("acme", "trading").getDesiredSessionState()).isEqualTo(FixSessionState.LOGGED_OUT);
    }

    @Test
    void anInitiatorRunningABackupIsUpdatedThroughItsMainConfig() {
        FixSessionSettings main = settingsOf("alpha", "trading");
        FixSessionId backup = paris.getInitiatorsTargets().stream()
                .filter(targets -> targets.getMainTarget().getFixSessionId().equals(main.getFixSessionId()))
                .findFirst().orElseThrow().getBackupTargets().getFirst().getFixSessionId();
        paris.switchInitiatorSession(backup);

        paris.updateFixSessionSettings(main.getFixSessionId(), main.toBuilder().testingMode(true).build());

        assertThat(session("alpha", "trading-drp").getFixSessionSettings().isTestingMode()).isTrue();
        assertThat(paris.findFixSessionsSettingsStore(main.getFixSessionId(), FixSessionType.INITIATOR)).isPresent();
        assertThatThrownBy(() -> paris.updateFixSessionSettings(main.getFixSessionId(), settingsOf("acme", "trading")
                .toBuilder().fixSessionType(FixSessionType.INITIATOR).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAcceptorSessionIsAddedAndRemoved() {
        FixSessionSettings hooli = settingsOf("acme", "trading").toBuilder()
                .fixSessionId(FixSessionId.of(FixRegularVersion.VERSION_44, FixSessionId.FixSessionIdBuilder.builder()
                        .group("hooli").name("trading").senderCompID("STAFFIX-PAR").targetCompID("HOOLI").build()))
                .build();

        paris.addFixSessionSettings("simulated", hooli);
        ticks(3);
        assertThat(session("hooli", "trading").isLoggedIn()).isTrue();
        assertThatThrownBy(() -> paris.addFixSessionSettings("simulated", hooli)).isInstanceOf(IllegalStateException.class);

        paris.removeFixSessionSettings(hooli.getFixSessionId(), FixSessionType.ACCEPTOR);
        assertThat(paris.getManagedFixSessions()).noneMatch(s -> s.getFixSessionId().equals(hooli.getFixSessionId()));
    }
}
