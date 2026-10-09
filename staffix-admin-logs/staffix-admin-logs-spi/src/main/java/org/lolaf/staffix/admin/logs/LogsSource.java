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

import java.util.List;

/**
 * A logs backend (Loki, ...) holding what staffix's messages loggers export: every message in and out, and session
 * events. Entries are selected by the attributes staffix's OTLP logger sets: {@code fix.eid}, {@code fix.sg},
 * {@code fix.sn} (session name) and {@code fix.log.type}.
 */
public interface LogsSource {

    /**
     * Newest first, at most {@link LogQuery#getLimit()} entries.
     *
     * @throws LogsSourceException if the backend cannot be queried
     */
    List<LogEntry> query(LogQuery query);

    /**
     * Probes the backend with a cheap request, so an admin knows a connector works before relying on it.
     *
     * @throws LogsSourceException if the backend does not answer as expected, or cannot be tested
     */
    default void testConnection() {
        throw new LogsSourceException("This logs backend cannot be tested", null);
    }
}
