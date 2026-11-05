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
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Engines replicating to each other, grouped by the pair name their status gives.
 */
@Value
@Builder
public class HaPair {
    /**
     * Null for an engine whose pair has no name, listed alone: nothing tells which engine it pairs with.
     */
    @Nullable
    String name;
    @Singular
    List<Node> nodes;

    @Value
    @Builder
    public static class Node {
        String engineId;
        /**
         * Null while the engine does not answer.
         */
        @Nullable
        HaStatus status;
        /**
         * What this node replicates or holds; empty while it does not answer.
         */
        @Singular
        List<HaSession> sessions;
    }
}
