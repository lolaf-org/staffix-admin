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
package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The parts of a staffix FixSessionId other than its group and id, sent apart rather than as its
 * {@code toString()} so the UI shows them without repeating the session id or parsing a format.
 */
@Value
@Builder
public class FixIdentity {
    /**
     * BeginString, e.g. {@code FIX.4.4} or {@code FIXT.1.1}.
     */
    String fixVersion;
    /**
     * DefaultApplVerID of a FIXT session, null otherwise.
     */
    @Nullable
    String defaultApplVerId;
    /**
     * Seen from this engine: the sender is the engine's side.
     */
    CompIds sender;
    CompIds target;
}
