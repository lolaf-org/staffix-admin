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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.alerting.Alert;
import org.lolaf.staffix.api.alerting.ProbeState;
import org.lolaf.staffix.api.ha.HaAdmin;
import org.lolaf.staffix.api.ha.HaSessionStatus;
import org.lolaf.staffix.api.ha.HaStatus;
import org.lolaf.staffix.api.ha.LeaderElection.Role;
import org.lolaf.staffix.api.ha.SwitchoverOutcome;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;
import org.lolaf.staffix.api.session.FixSessionId;
import org.lolaf.staffix.api.session.FixSessionSettings.HaSessionSettings.ReplicationMode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;

/**
 * Two engines replicating to each other, leadership moving on a script so the console's pair views change on their
 * own: a stalled follower, a snapshot, a clean handover, then a failover. An operator's switchover moves it at once.
 */
final class SimulatedHaPair {

    static final int STALL_TICK = 60;
    static final int SNAPSHOT_TICK = 75;
    static final int STEADY_AGAIN_TICK = 85;
    static final int HANDING_OVER_TICK = 95;
    static final int FAILOVER_TICK = 100;
    static final int NEW_LEADER_TICK = 105;
    static final int CYCLE_TICKS = 120;
    static final String REFUSAL = "its storage is shared with the leader";
    private static final int RESYNC_TICKS = 5;
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(1);
    /**
     * Staffix's {@code followerLagAlertThreshold} default.
     */
    private static final int LAG_ALERT_TICKS = 5;

    private final String pairName;
    private final Node[] nodes;
    private int tick;
    private int resyncTicks;
    private long epoch = 1;
    /**
     * Null while nobody leads, during a failover.
     */
    @Nullable
    private Node leader;
    private Node lastLeader;
    private Instant roleSince = Instant.now();
    private final Instant started = roleSince;
    /**
     * Who led from each instant, null while nobody did, so the meters read back what happened.
     */
    private final TreeMap<Instant, @Nullable Node> leaders = new TreeMap<>();

    SimulatedHaPair(String pairName, SimulatedEngine first, SimulatedEngine second) {
        this.pairName = pairName;
        this.nodes = new Node[]{new Node("1", first), new Node("2", second)};
        leader = nodes[0];
        lastLeader = nodes[0];
        leaders.put(Instant.EPOCH, nodes[0]);
        first.joinPair(nodes[0], true);
        second.joinPair(nodes[1], false);
    }

    private synchronized void tick() {
        tick = (tick + 1) % CYCLE_TICKS;
        resyncTicks = Math.max(0, resyncTicks - 1);
        if (tick == FAILOVER_TICK) {
            lead(null);
        } else if (tick == NEW_LEADER_TICK) {
            lead(other(lastLeader));
        }
        if (leader != null && !stalled()) {
            other(leader).engine.replicate(leader.engine);
        }
    }

    private void lead(@Nullable Node next) {
        for (Node node : nodes) {
            node.engine.setLeading(node == next);
        }
        if (next != null) {
            lastLeader = next;
            epoch++;
        }
        leader = next;
        roleSince = Instant.now();
        leaders.put(roleSince, next);
    }

    private Node other(Node node) {
        return node == nodes[0] ? nodes[1] : nodes[0];
    }

    private boolean stalled() {
        return tick >= STALL_TICK && tick < SNAPSHOT_TICK;
    }

    private boolean snapshotting() {
        return resyncTicks > 0 || tick >= SNAPSHOT_TICK && tick < STEADY_AGAIN_TICK;
    }

    private boolean handingOver() {
        return tick >= HANDING_OVER_TICK && tick < FAILOVER_TICK;
    }

    private static ReplicationMode mode(String sessionName) {
        return switch (sessionName) {
            case "market-data" -> ReplicationMode.NONE;
            case "trading" -> ReplicationMode.SYNC_APPLIED;
            case "drop-copy" -> ReplicationMode.SYNC_RECEIVED;
            default -> ReplicationMode.ASYNC;
        };
    }

    static boolean refused(FixSessionId id) {
        return id.getGroup().equals("globex") && id.getName().equals("drop-copy");
    }

    /**
     * One node's view of the pair.
     */
    @RequiredArgsConstructor
    final class Node implements HaAdmin {

        private final String nodeId;
        private final SimulatedEngine engine;
        private final SimulatedHaProbes probes = new SimulatedHaProbes();
        private boolean candidate = true;

        void tick() {
            if (this == nodes[0]) {
                SimulatedHaPair.this.tick();
            }
        }

        SimulatedHaProbes probes() {
            return probes;
        }

        String nodeId() {
            return nodeId;
        }

        /**
         * The script's tick at that instant, the script having run the same way before the simulator started.
         */
        int phaseAt(Instant t) {
            return (int) Math.floorMod(t.getEpochSecond() - started.getEpochSecond(), (long) CYCLE_TICKS);
        }

        boolean ledAt(Instant t) {
            synchronized (SimulatedHaPair.this) {
                return leaders.floorEntry(t).getValue() == this;
            }
        }

        /**
         * As staffix's {@code ha.role}: 2 leader, 1 follower, 0 unknown.
         */
        double roleAt(Instant t) {
            synchronized (SimulatedHaPair.this) {
                Node ledBy = leaders.floorEntry(t).getValue();
                return ledBy == null ? 0 : ledBy == this ? 2 : 1;
            }
        }

        /**
         * Returns the alerts raised, changed or cleared.
         */
        List<ProbeState> evaluateProbes() {
            synchronized (SimulatedHaPair.this) {
                return probes.evaluate(this::alert, Instant.now());
            }
        }

        private @Nullable Alert alert(String probe) {
            return switch (probe) {
                case SimulatedHaProbes.FOLLOWERS -> leader == this ? followersAlert() : null;
                case SimulatedHaProbes.LEADER_CONNECTION -> leader != null && leader != this && snapshotting()
                        ? Alert.warning("Connected to the leader, not holding every session in full") : null;
                default -> leader == null ? Alert.critical("This node has no role") : null;
            };
        }

        private @Nullable Alert followersAlert() {
            String follower = other(this).nodeId;
            int stalledTicks = stalled() ? tick - STALL_TICK + 1 : 0;
            if (stalledTicks > LAG_ALERT_TICKS) {
                return Alert.major("Follower " + follower + " has not applied a write for " + stalledTicks + "s");
            }
            return snapshotting() ? Alert.warning("Follower " + follower + " is catching up by snapshot") : null;
        }

        @Override
        public HaStatus getStatus() {
            synchronized (SimulatedHaPair.this) {
                boolean leads = leader == this;
                HaStatus.HaStatusBuilder status = HaStatus.builder()
                        .nodeId(nodeId)
                        .pairName(pairName)
                        .role(leader == null ? Role.UNKNOWN : leads ? Role.LEADER : Role.FOLLOWER)
                        .epoch(leader == null ? -1 : epoch)
                        .leaderId(leader == null ? null : leader.nodeId)
                        .leadershipValid(leads)
                        .leaderElectionType("SimulatedLeaderElection")
                        .roleSince(roleSince)
                        .transferLeadershipSupported(true)
                        .candidacySupported(true)
                        .candidate(candidate);
                if (leads) {
                    status.followerConnection(followerConnection());
                } else if (leader != null) {
                    status.leader(HaStatus.Leader.builder().leaderId(leader.nodeId).address("/10.0.0." + leader.nodeId + ":17101")
                            .connected(true).inSync(!snapshotting()).build());
                }
                return status.build();
            }
        }

        private HaStatus.FollowerConnection followerConnection() {
            int stalledTicks = stalled() ? tick - STALL_TICK + 1 : 0;
            Node follower = other(this);
            return HaStatus.FollowerConnection.builder()
                    .nodeId(follower.nodeId)
                    .address("/10.0.0." + follower.nodeId + ":40000")
                    .connected(true)
                    .inSync(!snapshotting())
                    .snapshotInProgress(snapshotting())
                    .unsentBytes(stalledTicks * 65_536L)
                    .lagMillis(stalledTicks * 1_000L)
                    .build();
        }

        @Override
        public List<HaSessionStatus> getSessionStatuses() {
            synchronized (SimulatedHaPair.this) {
                return engine.runningConfigs().stream()
                        .filter(session -> mode(session.name()) != ReplicationMode.NONE)
                        .map(this::sessionStatus)
                        .toList();
            }
        }

        private HaSessionStatus sessionStatus(SimulatedSession session) {
            boolean refused = refused(session.fixSessionId());
            boolean follows = leader != this;
            return HaSessionStatus.builder()
                    .fixSessionId(session.fixSessionId())
                    .fixSessionType(session.settings.getFixSessionType())
                    .replicationMode(mode(session.name()))
                    .replicationAckTimeout(ACK_TIMEOUT)
                    .inSync(!refused && !snapshotting())
                    .notReplicatedReason(refused ? REFUSAL : null)
                    .nextIncomingSeqNum(follows ? session.incomingSeqNum : -1)
                    .nextOutgoingSeqNum(follows ? session.outgoingSeqNum : -1)
                    .handedOver(follows && !refused && handingOver())
                    .build();
        }

        @Override
        public void setCandidate(boolean standing) {
            synchronized (SimulatedHaPair.this) {
                candidate = standing;
            }
        }

        @Override
        public void resync() {
            synchronized (SimulatedHaPair.this) {
                resyncTicks = RESYNC_TICKS;
            }
        }

        @Override
        public void resync(FixSessionId fixSessionId, FixSessionType fixSessionType) {
            resync();
        }

        @Override
        public SwitchoverOutcome switchover() {
            synchronized (SimulatedHaPair.this) {
                if (leader != this) {
                    throw new IllegalStateException("Node " + nodeId + " does not lead");
                }
                if (snapshotting()) {
                    return SwitchoverOutcome.NO_FOLLOWER_IN_SYNC;
                }
                if (!other(this).candidate) {
                    return SwitchoverOutcome.NOT_TRANSFERRED;
                }
                lead(other(this));
                return SwitchoverOutcome.TRANSFERRED;
            }
        }
    }
}
