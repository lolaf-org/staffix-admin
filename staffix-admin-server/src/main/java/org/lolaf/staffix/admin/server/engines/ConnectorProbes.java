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
package org.lolaf.staffix.admin.server.engines;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * What an engine's HTTP admin connector answers for a session's {@code probes} and {@code probes/states}, as it
 * sends it; intervals are ISO-8601 durations and times ISO-8601 instants.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConnectorProbes {

    @Value
    @Builder
    @Jacksonized
    static class Probe {
        String name;
        String description;
        String evaluationInterval;
        @Nullable
        String reminderInterval;
        @Nullable
        String runbookUrl;
    }

    @Value
    @Builder
    @Jacksonized
    static class State {
        String probe;
        @Nullable
        String level;
        @Nullable
        String message;
        @Nullable
        String since;
        String lastEvaluation;
    }
}
