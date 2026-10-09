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
package org.lolaf.staffix.admin.logs;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Value
@Builder
public class LogEntry {
    Instant timestamp;
    @Nullable
    String group;
    @Nullable
    String sessionId;
    @Nullable
    LogType type;
    /**
     * A FIX message with {@code |} between fields, or an event's text.
     */
    String line;
}
