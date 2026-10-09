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
package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The connector of each role: a connector id, {@link #NONE} when that telemetry is not collected, or null to fall
 * back on the next level.
 */
@Value
@Builder(toBuilder = true)
public class EngineConnectors {
    public static final String NONE = "none";

    @Nullable
    String metrics;
    @Nullable
    String logs;
}
