package org.lolaf.staffix.admin.server.connectors;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricKind;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * How an engine's emitters export, read from the settings staffix's HTTP connector serves ({@code GET components}).
 * The only place that knows staffix's settings field names; empty when the engine never answered.
 */
@Component
@RequiredArgsConstructor
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
    static final Map<String, Set<MetricKind>> METERS = Map.of(
            "readLatencyEnabled", EnumSet.of(MetricKind.READ_LATENCY, MetricKind.MESSAGE_RATE),
            "writeLatencyEnabled", EnumSet.of(MetricKind.WRITE_LATENCY, MetricKind.MESSAGE_RATE),
            "decodingLatencyEnabled", EnumSet.of(MetricKind.DECODING_LATENCY),
            "encodingLatencyEnabled", EnumSet.of(MetricKind.ENCODING_LATENCY),
            "rttLatencyEnabled", EnumSet.of(MetricKind.ROUND_TRIP_TIME),
            "clockOffsetEnabled", EnumSet.of(MetricKind.CLOCK_OFFSET));

    private final EngineGateway gateway;
    private final Clock clock;
    private final Map<String, Known> lastAnswers = new ConcurrentHashMap<>();

    public Optional<Logger> logger(String engineId, String instanceId) {
        return emitters(engineId).flatMap(emitters -> elements(emitters.path("messagesLoggers"))
                .filter(logger -> instanceId.equals(logger.path("instanceId").asString(null)))
                .findFirst()
                .map(Logger::of));
    }

    public Optional<Monitor> monitor(String engineId, String instanceId) {
        return emitters(engineId).flatMap(emitters -> elements(emitters.path("sessionsPlugins"))
                .filter(plugin -> instanceId.equals(plugin.path("instanceId").asString(null))
                        && elements(plugin.path("pluginTypes")).anyMatch(type -> MONITORING.equals(type.asString(null))))
                .findFirst()
                .map(Monitor::of));
    }

    private Optional<JsonNode> emitters(String engineId) {
        Instant now = clock.instant();
        Known last = lastAnswers.get(engineId);
        if (last != null && now.isBefore(last.at().plus(FRESH))) {
            return Optional.of(last.emitters());
        }
        try {
            JsonNode emitters = gateway.getEmitters(engineId);
            lastAnswers.put(engineId, new Known(now, emitters));
            return Optional.of(emitters);
        } catch (GatewayException unreachable) {
            return last == null ? Optional.empty() : Optional.of(last.emitters());
        }
    }

    private static Stream<JsonNode> elements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
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
     * @param kinds         the metrics its enabled meters feed; every kind when it does not say
     * @param exportStep    how often it pushes its meters over OTLP, which no backend can be finer than; null when
     *                      it does not say
     */
    public record Monitor(List<String> types, @Nullable String otlpEndpoint, @Nullable TimeUnit timerBaseUnit, Set<MetricKind> kinds,
                          @Nullable Duration exportStep) {

        static Monitor of(JsonNode settings) {
            List<JsonNode> chain = chain(settings);
            JsonNode meters = chain.stream().filter(node -> node.has("readLatencyEnabled")).findFirst().orElse(null);
            String unit = chain.stream().map(node -> node.path("baseTimeUnit").asString(null)).filter(Objects::nonNull).findFirst().orElse(null);
            Set<MetricKind> kinds = EnumSet.allOf(MetricKind.class);
            if (meters != null) {
                kinds = EnumSet.noneOf(MetricKind.class);
                for (Map.Entry<String, Set<MetricKind>> meter : METERS.entrySet()) {
                    if (meters.path(meter.getKey()).asBoolean(false)) {
                        kinds.addAll(meter.getValue());
                    }
                }
            }
            return new Monitor(typesOf(chain), otlpEndpointOf(chain), unit == null ? null : TimeUnit.valueOf(unit), kinds,
                    exportStepOf(chain));
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

    private record Known(Instant at, JsonNode emitters) {
    }
}
