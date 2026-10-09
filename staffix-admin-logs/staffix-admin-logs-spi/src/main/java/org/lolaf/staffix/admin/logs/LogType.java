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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.monitoring.FixMessageDirection;

/**
 * A staffix message log record: a message received, a message sent, or a session event, which staffix writes without
 * {@code fix.md}.
 */
@Getter
@RequiredArgsConstructor
public enum LogType {
    IN(FixMessageDirection.IN),
    OUT(FixMessageDirection.OUT),
    EVENT(null);

    /**
     * Null for {@link #EVENT}.
     */
    private final @Nullable FixMessageDirection direction;

    /**
     * Reads a record's {@code fix.md} value, missing or empty for an event; null for a value staffix does not write.
     */
    public static @Nullable LogType ofDirectionValue(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return EVENT;
        }
        return FixMessageDirection.ofValue(value).map(direction -> direction == FixMessageDirection.IN ? IN : OUT).orElse(null);
    }
}
