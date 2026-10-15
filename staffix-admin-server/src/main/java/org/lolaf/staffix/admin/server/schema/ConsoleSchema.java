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
package org.lolaf.staffix.admin.server.schema;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.connectors.ConnectorKind;
import org.lolaf.staffix.admin.server.connectors.ConnectorKind.Setting;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The schema of {@code console.yaml}. A kind no plugin of the build provides is accepted with any settings, as a
 * third-party plugin may provide it at runtime.
 */
@UtilityClass
class ConsoleSchema {

    private static final SchemaNodes NODES = new SchemaNodes("\\$\\{[a-z][a-z0-9-]*:[^}]*}");
    private static final String CONNECTOR_ID = "A connector id, or none when this telemetry is not collected.";

    static ObjectNode of(List<ConnectorKind> kinds) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("$schema", SchemaNodes.DRAFT);
        schema.put("title", "staffix-admin console.yaml");
        schema.setAll(NODES.object("The console's connectors and trace settings, also edited from its Connectors tab."));
        ObjectNode connectors = NODES.typed("array");
        connectors.put("description", "The backends telemetry is read from.");
        connectors.set("items", connector(kinds));
        NODES.exactProperty(schema, "connectors", connectors);
        NODES.exactProperty(schema, "defaults", engineConnectors("For every engine without its own entry, and every role "
                + "that entry leaves unset."));
        NODES.exactProperty(schema, "engines", NODES.map(engine(), "By engine id."));
        NODES.exactProperty(schema, "trace", trace());
        return schema;
    }

    private static ObjectNode connector(List<ConnectorKind> kinds) {
        ObjectNode connector = NODES.object(null);
        NODES.exactProperty(connector, "id", nonBlank("Referenced by the defaults and the engines."));
        NODES.exactProperty(connector, "name", nonBlank("As the console shows it."));
        ObjectNode kind = nonBlank("Which plugin reads this backend, and so which settings apply.");
        ArrayNode examples = kind.putArray("examples");
        kinds.forEach(known -> examples.add(known.getKind()));
        NODES.exactProperty(connector, "kind", kind);
        ObjectNode settings = NODES.typed("object");
        settings.put("description", "As the kind defines them. A string may be a reference such as ${env:NAME} or "
                + "${file:/run/secrets/name}.");
        NODES.exactProperty(connector, "settings", settings);
        Arrays.asList("id", "name", "kind").forEach(name -> NODES.exactRequire(connector, name));
        kinds.forEach(known -> connector.withArray("allOf").add(whenKind(known)));
        return connector;
    }

    private static ObjectNode whenKind(ConnectorKind kind) {
        ObjectNode when = JsonNodeFactory.instance.objectNode();
        ObjectNode condition = when.putObject("if");
        condition.putObject("properties").putObject("kind").put("const", kind.getKind());
        condition.putArray("required").add("kind");
        ObjectNode then = when.putObject("then");
        then.putObject("properties").set("settings", settings(kind));
        if (kind.getSettings().stream().anyMatch(Setting::isRequired)) {
            then.putArray("required").add("settings");
        }
        return when;
    }

    private static ObjectNode settings(ConnectorKind kind) {
        ObjectNode settings = NODES.object(kind.getRole().name().toLowerCase(Locale.ROOT) + " settings of " + kind.getKind() + ".");
        for (Setting setting : kind.getSettings()) {
            NODES.property(settings, setting.getName(), setting(setting));
            if (setting.isRequired()) {
                NODES.require(settings, setting.getName());
            }
        }
        return settings;
    }

    private static ObjectNode setting(Setting setting) {
        String description = setting.isSecret()
                ? join(setting.getDescription(), "A credential: a reference, or typed in the console, which stores it encrypted.")
                : setting.getDescription();
        ObjectNode schema = switch (setting.getType()) {
            case TEXT, URL -> NODES.text(description);
            case DURATION -> NODES.duration(description);
            case NUMBER -> NODES.number(description);
            case BOOLEAN -> NODES.bool(description);
            case ENUM -> NODES.oneOf(setting.getValues(), description);
            case LIST -> NODES.list(setting.getValues().isEmpty() ? NODES.text(null) : NODES.oneOf(setting.getValues(), null), description);
        };
        if (setting.getDefaultValue() != null) {
            schema.set("default", defaultValue(setting));
        }
        return schema;
    }

    private static JsonNode defaultValue(Setting setting) {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        String value = setting.getDefaultValue();
        return switch (setting.getType()) {
            case BOOLEAN -> nodes.booleanNode(Boolean.parseBoolean(value));
            case NUMBER -> nodes.numberNode(new BigDecimal(value));
            case LIST -> {
                ArrayNode elements = nodes.arrayNode();
                Arrays.stream(value.split(",")).map(String::trim).forEach(elements::add);
                yield elements;
            }
            default -> nodes.stringNode(value);
        };
    }

    private static ObjectNode engine() {
        ObjectNode engine = NODES.object(null);
        NODES.exactProperty(engine, "defaults", engineConnectors("For an emitter without its own entry; a role left unset "
                + "falls back on the top-level defaults."));
        NODES.exactProperty(engine, "loggers", NODES.map(nonBlank(CONNECTOR_ID), "Logs connector by messages logger instance id."));
        NODES.exactProperty(engine, "monitors", NODES.map(nonBlank(CONNECTOR_ID), "Metrics connector by monitoring plugin instance id."));
        return engine;
    }

    private static ObjectNode engineConnectors(String description) {
        ObjectNode connectors = NODES.object(description);
        NODES.exactProperty(connectors, "metrics", nonBlank(CONNECTOR_ID));
        NODES.exactProperty(connectors, "logs", nonBlank(CONNECTOR_ID));
        return connectors;
    }

    private static ObjectNode trace() {
        ObjectNode trace = NODES.object("How the order trace follows an id. A list left out means the console's "
                + "defaults, an empty one none.");
        ObjectNode linkingTags = NODES.typed("array");
        linkingTags.put("description", "The tags whose values tie the messages of one order or quote together, e.g. 11, 37.");
        linkingTags.set("items", tag());
        NODES.exactProperty(trace, "linkingTags", linkingTags);
        ObjectNode keyField = NODES.object(null);
        NODES.exactProperty(keyField, "tag", tag());
        NODES.exactProperty(keyField, "name", nonBlank("As the trace shows it."));
        NODES.exactRequire(keyField, "tag");
        NODES.exactRequire(keyField, "name");
        ObjectNode keyFields = NODES.typed("array");
        keyFields.put("description", "The fields shown for each message, in this order.");
        keyFields.set("items", keyField);
        NODES.exactProperty(trace, "keyFields", keyFields);
        return trace;
    }

    private static ObjectNode tag() {
        ObjectNode tag = NODES.typed("integer");
        tag.put("minimum", 1);
        return tag;
    }

    private static ObjectNode nonBlank(String description) {
        ObjectNode text = NODES.typed("string");
        text.put("pattern", "\\S");
        text.put("description", description);
        return text;
    }

    private static String join(@Nullable String description, String more) {
        return description == null ? more : description + " " + more;
    }
}
