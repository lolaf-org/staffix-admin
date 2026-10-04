package org.lolaf.staffix.admin.server.simulator;

import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Emitters shaped as staffix's {@code GET components} answers, chosen by instance id so the scenario shows each case:
 * an OTLP logger, one behind an async wrapper, a logger that exports nothing, and monitoring plugins with different
 * meters.
 */
@UtilityClass
class SimulatedEmitters {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String MONITORING = "FixSessionsMonitoringManager";

    JsonNode of(Collection<SimulatedConnection.Emitters> used) {
        ObjectNode emitters = JSON.objectNode();
        ArrayNode loggers = emitters.putArray("messagesLoggers");
        new TreeSet<>(used.stream().map(SimulatedConnection.Emitters::messagesLogger).toList())
                .forEach(instanceId -> loggers.add(logger(instanceId)));
        ArrayNode plugins = emitters.putArray("sessionsPlugins");
        new TreeSet<>(used.stream().map(SimulatedConnection.Emitters::monitoring).filter(Objects::nonNull).toList())
                .forEach(instanceId -> plugins.add(monitoring(instanceId)));
        return emitters;
    }

    private static ObjectNode logger(String instanceId) {
        if (instanceId.startsWith("slf4j")) {
            return settings("Slf4jMessagesLoggerSettings", instanceId);
        }
        ObjectNode otlp = settings("OtlpMessagesLoggerSettings", instanceId).put("otlpEndpointUrl", collector(instanceId));
        if (!instanceId.startsWith("clients")) {
            return otlp;
        }
        ObjectNode async = settings("AsyncMessagesLoggerSettings", instanceId);
        async.set("wrappedLoggerSettings", otlp);
        return async;
    }

    private static ObjectNode monitoring(String instanceId) {
        boolean clients = instanceId.startsWith("clients");
        ObjectNode micrometer = settings("OtlpMicrometerMonitoringManagerSettings", instanceId)
                .put("otlpEndpointUrl", collector(instanceId))
                .put("baseTimeUnit", "MICROSECONDS")
                .put("readLatencyEnabled", true)
                .put("writeLatencyEnabled", true)
                .put("decodingLatencyEnabled", !clients)
                .put("encodingLatencyEnabled", !clients)
                .put("rttLatencyEnabled", true)
                .put("clockOffsetEnabled", !clients);
        micrometer.putArray("pluginTypes").add(MONITORING);
        if (!clients) {
            return micrometer;
        }
        ObjectNode async = settings("AsyncFixSessionsPluginSettings", instanceId);
        async.set("delegateSettings", micrometer);
        async.putArray("pluginTypes").add("AsyncFixSessionsPlugin").add(MONITORING);
        return async;
    }

    private static ObjectNode settings(String type, String instanceId) {
        return JSON.objectNode().put("type", type).put("instanceId", instanceId);
    }

    private static String collector(String instanceId) {
        return instanceId.startsWith("clients") ? "http://otel-clients:4318" : "http://otel-collector:4318";
    }
}
