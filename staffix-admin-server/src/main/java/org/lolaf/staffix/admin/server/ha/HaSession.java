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

/**
 * A replicated session as one node sees it, as its {@code v1/ha/sessions} answers.
 */
@Value
@Builder
@Jacksonized
public class HaSession {
    String group;
    String name;
    String fixSessionType;
    /**
     * Kept as text: the console only shows it, and an engine may add modes.
     */
    String replicationMode;
    Duration replicationAckTimeout;
    boolean inSync;
    /**
     * Why a follower refuses it; null when it is replicated.
     */
    @Nullable
    String notReplicatedReason;
    boolean lastSendUnacknowledged;
    long unacknowledgedSends;
    /**
     * -1 on a leader, or on a follower holding none.
     */
    long nextIncomingSeqNum;
    long nextOutgoingSeqNum;
    /**
     * Taking it over keeps its outgoing sequence number.
     */
    boolean handedOver;
    /**
     * What the follower has yet to store, which delays its takeover though it reads as in sync.
     */
    long pendingWrites;
    /**
     * Messages this node's store gave up on since its last reset. A re-sync clears it on the follower by copying the
     * leader's store, holes included.
     */
    long lostWrites;
}
