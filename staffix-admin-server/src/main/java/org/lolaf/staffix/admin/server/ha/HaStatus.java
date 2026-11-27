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
package org.lolaf.staffix.admin.server.ha;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * An engine's high availability, as its {@code v1/ha} answers.
 */
@Value
@Builder
@Jacksonized
public class HaStatus {
    String nodeId;
    /**
     * Shared by the nodes of a pair; null when the pair is unnamed.
     */
    @Nullable
    String pairName;
    HaRole role;
    /**
     * -1 when unknown.
     */
    long epoch;
    @Nullable
    String leaderId;
    boolean leadershipValid;
    String leaderElectionType;
    @Nullable
    Instant roleSince;
    boolean transferLeadershipSupported;
    boolean electableSupported;
    boolean electable;
    /**
     * The longest a switchover can take on this engine.
     */
    @Nullable
    Duration switchoverMaxDuration;
    /**
     * The monitoring plugin publishing the node's own meters, which sessions may not use; null when it publishes none.
     */
    @Nullable
    String monitoringInstanceId;
    /**
     * The other nodes: while this node leads, the followers it expects, connected or not.
     */
    @Builder.Default
    List<String> peers = List.of();
    /**
     * The followers a {@code SYNC} send waits for, this node left out, connected or not; empty makes every follower one.
     */
    @Builder.Default
    List<String> syncFollowers = List.of();
    /**
     * One entry per follower and lane, while this node leads.
     */
    List<FollowerConnection> followerConnections;
    List<DroppedFollower> droppedFollowers;
    /**
     * This node's connection to its leader, while it follows one.
     */
    @Nullable
    Leader leader;
    /**
     * While this node follows: how its session settings differ from the leader's, as a failover runs its own.
     */
    @Builder.Default
    List<SessionSettingsDifference> sessionSettingsDifferences = List.of();
    /**
     * While this node does not lead: the last write it holds on each lane, to choose which follower to promote.
     */
    @Builder.Default
    List<LanePosition> lanePositions = List.of();

    @Value
    @Builder
    @Jacksonized
    public static class FollowerConnection {
        @Nullable
        String nodeId;
        int lane;
        String address;
        boolean connected;
        /**
         * As of its last acknowledgement: a stalled follower still reads true, {@link #lagMillis} shows it.
         */
        boolean inSync;
        /**
         * One a {@code SYNC} send waits for: a failover to one in sync loses nothing acknowledged, unlike a
         * disaster-recovery copy.
         */
        boolean syncFollower;
        boolean snapshotInProgress;
        /**
         * -1 before the leader started sending.
         */
        long unsentBytes;
        /**
         * An offset in the lane's replication log, -1 before the follower applied a write: only the gap between two
         * followers of a lane means anything, in bytes.
         */
        long appliedOffset;
        long lagMillis;
        /**
         * How long the lane's last return to sync took, snapshots included; -1 before the first, null from an engine
         * before staffix 0.9.2.
         */
        @Nullable
        Long catchUpMillis;
        /**
         * The cipher encrypting this lane; null in clear or while not connected.
         */
        @Nullable
        String cipher;
    }

    @Value
    @Builder
    @Jacksonized
    public static class DroppedFollower {
        String nodeId;
        int lane;
        Instant droppedAt;
        /**
         * Null once it may connect.
         */
        @Nullable
        Instant heldOffUntil;
    }

    @Value
    @Builder
    @Jacksonized
    public static class Leader {
        @Nullable
        String leaderId;
        String address;
        boolean connected;
        boolean inSync;
        /**
         * The cipher encrypting every lane from the leader; null when one is in clear or not connected.
         */
        @Nullable
        String cipher;
    }

    /**
     * Two nodes' positions compare only at the same epoch and leadership.
     */
    @Value
    @Builder
    @Jacksonized
    public static class LanePosition {
        int lane;
        long epoch;
        long leadership;
        /**
         * -1 for none since the lane last connected without resuming.
         */
        long offset;
    }

    @Value
    @Builder
    @Jacksonized
    public static class SessionSettingsDifference {
        String store;
        String fixSessionType;
        /**
         * The session's full form, {@code group.name:VERSION:SENDER->TARGET}.
         */
        String fixSessionId;
        Kind kind;

        public enum Kind {
            MISSING_HERE, EXTRA_HERE, DIFFERENT
        }
    }
}
