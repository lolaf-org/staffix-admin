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
    String leaderElection;
    @Nullable
    Instant roleSince;
    boolean transferLeadershipSupported;
    boolean candidacySupported;
    boolean candidate;
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
     * One entry per follower and replication link, while this node leads.
     */
    List<Follower> followers;
    List<DroppedFollower> droppedFollowers;
    /**
     * This node's link to its leader, while it follows one.
     */
    @Nullable
    Leader leader;

    @Value
    @Builder
    @Jacksonized
    public static class Follower {
        @Nullable
        String nodeId;
        int link;
        String address;
        boolean connected;
        /**
         * As of its last acknowledgement: a stalled follower still reads true, {@link #lagMillis} shows it.
         */
        boolean inSync;
        boolean snapshotInProgress;
        /**
         * -1 before the leader started sending.
         */
        long unsentBytes;
        long lagMillis;
    }

    @Value
    @Builder
    @Jacksonized
    public static class DroppedFollower {
        String nodeId;
        int link;
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
    }
}
