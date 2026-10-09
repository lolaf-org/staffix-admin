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
package org.lolaf.staffix.admin.server.connectors;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The emitters an engine's sessions use, so an admin sees what a connector assignment covers. Sessions are named
 * {@code group.session}.
 */
@Value
@Builder
public class EngineEmitters {
    String engineId;
    /**
     * False for an engine known from the stored connectors only.
     */
    boolean announced;
    /**
     * Whether its sessions could be read; its emitters are empty otherwise.
     */
    boolean reachable;
    @Singular
    List<Emitter> loggers;
    @Singular
    List<Emitter> monitors;
    /**
     * Sessions without a monitoring plugin, whose metrics are not collected.
     */
    @Singular
    List<String> unmonitoredSessions;

    @Value
    @Builder
    public static class Emitter {
        String instanceId;
        @Singular
        List<String> sessions;
        /**
         * Its staffix settings types, wrapper first; empty when the engine did not say.
         */
        @Singular
        List<String> types;
        /**
         * Where it sends over OTLP, null when it does not or the engine did not say.
         */
        @Nullable
        String otlpEndpoint;
        /**
         * False for a logger known to export nothing a backend reads (slf4j, file): its sessions' logs are not
         * collected whatever connector it is assigned.
         */
        boolean exports;
    }
}
