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

import java.util.Map;

/**
 * Where an engine's telemetry is read from, by emitter: a session's logs go through one of the engine's messages
 * loggers and its metrics through one of its monitoring plugins, each possibly reporting to another backend.
 */
@Value
@Builder(toBuilder = true)
public class EngineMapping {
    /**
     * For an emitter without its own entry; a role left null falls back on {@link ConsoleConfig#getDefaults()}.
     */
    @Builder.Default
    EngineConnectors defaults = EngineConnectors.builder().build();
    /**
     * Logs connector id, or {@link EngineConnectors#NONE}, by messages logger instance id.
     */
    @Singular
    Map<String, String> loggers;
    /**
     * Metrics connector id, or {@link EngineConnectors#NONE}, by monitoring plugin instance id.
     */
    @Singular
    Map<String, String> monitors;
}
