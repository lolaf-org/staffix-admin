/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.simulator;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.api.ComponentProbeScope;
import org.lolaf.staffix.api.alerting.ProbeId;
import org.lolaf.staffix.api.ha.HaAdmin;
import org.lolaf.staffix.api.ha.HaSessionStatus;
import org.lolaf.staffix.api.ha.LeaderElection.Role;
import org.lolaf.staffix.api.ha.SwitchoverOutcome;
import org.lolaf.staffix.api.session.FixSession;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedHaPairTest {

    private final List<SimulatedEngine> engines = SimulationScenario.engines();
    private final SimulatedEngine first = engine("geneva-a");
    private final SimulatedEngine second = engine("geneva-b");
    private final HaAdmin a = first.getHa().orElseThrow();
    private final HaAdmin b = second.getHa().orElseThrow();
    private final Random random = new Random(42);

    private SimulatedEngine engine(String engineId) {
        return engines.stream().filter(engine -> engine.getInstanceId().equals(engineId)).findFirst().orElseThrow();
    }

    private void ticksUntil(int tick) {
        for (int i = 0; i < tick; i++) {
            first.tick(random);
            second.tick(random);
        }
    }

    @Test
    void onlyThePairHasHighAvailabilityAndBothNodesShareItsName() {
        assertThat(engine("paris-prod").getHa()).isEmpty();
        assertThat(a.getStatus().getPairName()).isEqualTo("geneva-gateway").isEqualTo(b.getStatus().getPairName());
        assertThat(a.getStatus().getRole()).isEqualTo(Role.LEADER);
        assertThat(b.getStatus().getRole()).isEqualTo(Role.FOLLOWER);
    }

    @Test
    void theStandbyRefusesSessionActionsAndHoldsItsSessionsDown() {
        ticksUntil(10);
        FixSession onStandby = second.getManagedFixSessions().getFirst();

        assertThatThrownBy(() -> second.logonSession(onStandby.getFixSessionId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not lead");
        assertThat(second.runningConfigs()).noneMatch(session -> session.loggedIn);
        assertThat(first.runningConfigs()).anyMatch(session -> session.loggedIn);
    }

    @Test
    void theFollowerReportsSettingsThatDifferForAPartOfTheScript() {
        assertThat(b.getStatus().getSessionSettingsDifferences()).isEmpty();

        ticksUntil(SimulatedHaPair.SETTINGS_DIFFER_TICK);

        assertThat(a.getStatus().getSessionSettingsDifferences()).isEmpty();
        assertThat(b.getStatus().getSessionSettingsDifferences()).singleElement()
                .satisfies(difference -> assertThat(difference.getFixSessionId()).startsWith("acme.drop-copy:"));
    }

    @Test
    void aStalledFollowerStaysInSyncWhileItsLagGrows() {
        ticksUntil(SimulatedHaPair.STALL_TICK + 5);

        assertThat(a.getStatus().getFollowerConnections()).singleElement().satisfies(follower -> {
            assertThat(follower.isInSync()).isTrue();
            assertThat(follower.getLagMillis()).isEqualTo(6_000);
        });
    }

    @Test
    void aFailoverLeavesThePairWithoutALeaderThenTheOtherNodeLeads() {
        ticksUntil(SimulatedHaPair.FAILOVER_TICK);
        assertThat(a.getStatus().getRole()).isEqualTo(Role.UNKNOWN);
        assertThat(b.getStatus().getRole()).isEqualTo(Role.UNKNOWN);

        ticksUntil(SimulatedHaPair.NEW_LEADER_TICK - SimulatedHaPair.FAILOVER_TICK);
        assertThat(b.getStatus().getRole()).isEqualTo(Role.LEADER);
        assertThat(a.getStatus().getRole()).isEqualTo(Role.FOLLOWER);
    }

    @Test
    void aSwitchoverMovesLeadershipAtOnceUnlessTheFollowerCannotTakeIt() {
        b.setCandidate(false);
        assertThat(a.switchover()).isEqualTo(SwitchoverOutcome.NOT_TRANSFERRED);

        b.setCandidate(true);
        assertThat(a.switchover()).isEqualTo(SwitchoverOutcome.TRANSFERRED);
        assertThat(b.getStatus().getRole()).isEqualTo(Role.LEADER);

        b.resync();
        assertThat(b.switchover()).isEqualTo(SwitchoverOutcome.NO_FOLLOWER_IN_SYNC);
        assertThatThrownBy(a::switchover).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRefusedSessionIsInAlertOnTheLeaderOnly() {
        ticksUntil(2);

        assertThat(first.getActiveAlerts()).anyMatch(alert -> alert.getProbeState().getProbeName().equals("ha-replication"));
        assertThat(second.getActiveAlerts()).noneMatch(alert -> alert.getProbeState().getProbeName().equals("ha-replication"));

        a.switchover();
        ticksUntil(2);
        assertThat(first.getActiveAlerts()).noneMatch(alert -> alert.getProbeState().getProbeName().equals("ha-replication"));
        assertThat(second.getActiveAlerts()).anyMatch(alert -> alert.getProbeState().getProbeName().equals("ha-replication"));
    }

    private List<String> haAlerts(SimulatedEngine engine) {
        return engine.getActiveAlerts().stream()
                .filter(alert -> alert.getScope().getType().equals(ComponentProbeScope.TYPE))
                .map(alert -> alert.getProbeState().getProbeName())
                .toList();
    }

    @Test
    void theComponentAlertsFollowTheScript() {
        assertThat(first.getComponentProbeScopes()).singleElement().satisfies(scope -> assertThat(scope.getName()).isEqualTo("ha"));
        ticksUntil(SimulatedHaPair.STALL_TICK + 7);
        assertThat(haAlerts(first)).containsExactly("ha-followers");
        assertThat(haAlerts(second)).isEmpty();

        ticksUntil(SimulatedHaPair.SNAPSHOT_TICK - SimulatedHaPair.STALL_TICK - 7 + 1);
        assertThat(haAlerts(second)).containsExactly("ha-leader-connection");

        ticksUntil(SimulatedHaPair.FAILOVER_TICK - SimulatedHaPair.SNAPSHOT_TICK - 1);
        assertThat(haAlerts(first)).containsExactly("ha-leadership");
        assertThat(haAlerts(second)).containsExactly("ha-leadership");
    }

    @Test
    void aComponentAlertCannotBeClearedByHand() {
        ticksUntil(SimulatedHaPair.FAILOVER_TICK);

        assertThat(first.clearAlert(ProbeId.of(first.getComponentProbeScopes().getFirst(), "ha-leadership"))).isFalse();
    }

    @Test
    void sessionsShowEveryStandbyState() {
        List<HaSessionStatus> onFollower = b.getSessionStatuses();

        assertThat(onFollower).noneMatch(session -> session.getFixSessionId().getName().equals("market-data"));
        assertThat(onFollower).filteredOn(session -> session.getNotReplicatedReason() != null).singleElement()
                .satisfies(session -> assertThat(session.getFixSessionId().getGroup()).isEqualTo("globex"));

        ticksUntil(SimulatedHaPair.HANDING_OVER_TICK);
        assertThat(b.getSessionStatuses()).anyMatch(HaSessionStatus::isHandedOver);
    }
}
