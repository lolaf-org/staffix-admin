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

import java.time.Instant;
import java.util.Comparator;

/**
 * A probe in alert on an engine's session.
 */
@Value
@Builder
public class ActiveAlert {

    /**
     * The order every alert list is in: the worst level first, then the oldest.
     */
    public static final Comparator<ActiveAlert> WORST_FIRST =
            Comparator.comparing(ActiveAlert::getLevel).reversed().thenComparing(ActiveAlert::getSince);

    String engineId;
    String group;
    /**
     * The session's id, its main config's for an initiator.
     */
    String sessionId;
    /**
     * The config the alert was raised on: differs from the session id while an initiator runs a backup.
     */
    String configId;
    String probe;
    AlertLevel level;
    String message;
    /**
     * When the alert was raised, kept across level changes.
     */
    Instant since;
}
