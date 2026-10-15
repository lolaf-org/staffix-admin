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
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class ConsoleConfig {
    public static final ConsoleConfig EMPTY = ConsoleConfig.builder().build();

    /**
     * Identifies the stored content, set by the store on load and save; null before the first save. A save based on
     * another revision than the stored one is refused, so two admins, or an admin and a hand edit, cannot silently
     * overwrite each other.
     */
    @Nullable
    String revision;
    @Singular
    List<Connector> connectors;
    /**
     * For every engine without its own entry in {@link #getEngines()}, and for a role that entry leaves null.
     */
    @Builder.Default
    EngineConnectors defaults = EngineConnectors.builder().build();
    /**
     * By engine id, kept for engines currently absent: the registry only knows engines that announced themselves.
     */
    @Singular
    Map<String, EngineMapping> engines;
    /**
     * Null for the console's defaults.
     */
    @Nullable
    TraceConfig trace;
}
