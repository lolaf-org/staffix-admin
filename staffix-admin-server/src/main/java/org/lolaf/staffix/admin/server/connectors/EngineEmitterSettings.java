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

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * How an engine's emitters export, read from the settings staffix's HTTP connector serves ({@code GET components}).
 * The only place that knows staffix's settings field names; empty when the engine never answered.
 */
@Component
public class EngineEmitterSettings {

    /**
     * Emitters only change when an engine restarts with another configuration.
     */
    private static final Duration FRESH = Duration.ofMinutes(1);
    static final String MONITORING = "FixSessionsMonitoringManager";
    static final String OTLP_LOGGER = "OtlpMessagesLoggerSettings";
    static final String OTLP_MONITOR = "OtlpMicrometerMonitoringManagerSettings";
    /**
     * Loggers known to export nothing a backend can read; any other, a demux included, may.
     */
    static final Set<String> NOT_EXPORTING = Set.of("Slf4jMessagesLoggerSettings", "FileMessagesLoggerSettings",
            "AsyncMessagesLoggerSettings");
    private final LastAnswers<JsonNode> emitters;

    public EngineEmitterSettings(EngineGateway gateway, Clock clock) {
        this.emitters = new LastAnswers<>(FRESH, clock, gateway::getEmitters);
    }

    public Optional<Logger> logger(String engineId, String instanceId) {
        return emitters.of(engineId).flatMap(components -> components.path("messagesLoggers").valueStream()
                .filter(logger -> instanceId.equals(logger.path("instanceId").asString(null)))
                .findFirst()
                .map(Logger::of));
    }

    public Optional<Monitor> monitor(String engineId, String instanceId) {
        return emitters.of(engineId).flatMap(components -> components.path("sessionsPlugins").valueStream()
                .filter(plugin -> instanceId.equals(plugin.path("instanceId").asString(null))
                        && plugin.path("pluginTypes").valueStream().anyMatch(type -> MONITORING.equals(type.asString(null))))
                .findFirst()
                .map(Monitor::of));
    }

    private static List<JsonNode> chain(JsonNode settings) {
        List<JsonNode> chain = new ArrayList<>();
        chain.add(settings);
        settings.properties().forEach(property -> {
            if (property.getValue().isObject() && property.getValue().has("type")) {
                chain.addAll(chain(property.getValue()));
            }
        });
        return chain;
    }

    private static List<String> typesOf(List<JsonNode> chain) {
        return chain.stream().map(settings -> settings.path("type").asString("?")).toList();
    }

    private static @Nullable String otlpEndpointOf(List<JsonNode> chain) {
        return chain.stream()
                .map(settings -> settings.path("otlpEndpointUrl").asString(null))
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * @param types   its settings types, wrapper first
     * @param exports false when every logger of its chain is known to export nothing a backend reads
     */
    public record Logger(List<String> types, @Nullable String otlpEndpoint, boolean exports) {

        static Logger of(JsonNode settings) {
            List<JsonNode> chain = chain(settings);
            List<String> types = typesOf(chain);
            boolean exports = types.contains(OTLP_LOGGER) || !NOT_EXPORTING.containsAll(types);
            return new Logger(types, otlpEndpointOf(chain), exports);
        }
    }

    /**
     * @param types         its settings types, wrapper first
     * @param timerBaseUnit the unit its timers are exported in, null when it does not say
     * @param exportStep    how often it pushes its meters over OTLP, which no backend can be finer than; null when
     *                      it does not say
     */
    public record Monitor(List<String> types, @Nullable String otlpEndpoint, @Nullable TimeUnit timerBaseUnit, @Nullable Duration exportStep) {

        static Monitor of(JsonNode settings) {
            List<JsonNode> chain = chain(settings);
            String unit = chain.stream().map(node -> node.path("baseTimeUnit").asString(null)).filter(Objects::nonNull).findFirst().orElse(null);
            return new Monitor(typesOf(chain), otlpEndpointOf(chain), unit == null ? null : TimeUnit.valueOf(unit), exportStepOf(chain));
        }

        private static @Nullable Duration exportStepOf(List<JsonNode> chain) {
            String step = chain.stream()
                    .filter(node -> OTLP_MONITOR.equals(node.path("type").asString(null)))
                    .map(node -> node.path("step").asString(null))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            try {
                return step == null ? null : Duration.parse(step);
            } catch (DateTimeParseException notADuration) {
                return null;
            }
        }
    }
}
