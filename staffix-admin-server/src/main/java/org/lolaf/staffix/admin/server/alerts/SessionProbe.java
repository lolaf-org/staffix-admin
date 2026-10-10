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
package org.lolaf.staffix.admin.server.alerts;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * A probe of a session with where it stood at its last evaluation; level, message and since are null while it is
 * normal, and every state field is null before its first evaluation.
 */
@Value
@Builder
public class SessionProbe {
    String name;
    String description;
    Duration evaluationInterval;
    /**
     * Null for a probe that publishes transitions only.
     */
    @Nullable
    Duration reminderInterval;
    @Nullable
    AlertLevel level;
    @Nullable
    String message;
    @Nullable
    Instant since;
    @Nullable
    Instant lastEvaluation;
}
