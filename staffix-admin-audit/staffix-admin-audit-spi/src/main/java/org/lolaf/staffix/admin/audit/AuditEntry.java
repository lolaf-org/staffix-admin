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
package org.lolaf.staffix.admin.audit;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A change an operator made, or tried to make, through the console. Never holds a secret: connector changes name
 * what changed, not the values.
 */
@Value
@Builder
public class AuditEntry {
    Instant at;
    /**
     * The operator's login, {@code ?} when unauthenticated.
     */
    String user;
    /**
     * What was done, e.g. {@code logon} or {@code connectors: added connector x}.
     */
    String operation;
    AuditOutcome outcome;
    /**
     * Why it failed; null when it succeeded.
     */
    @Nullable
    String reason;
    /**
     * The session acted on, null for a change that is not a session's, such as the connectors.
     */
    @Nullable
    String engineId;
    @Nullable
    String group;
    @Nullable
    String sessionId;
}
