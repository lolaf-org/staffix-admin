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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value schemas accepting whatever Spring's binding accepts, so a file the console reads is never flagged: relaxed
 * names, enums in either case, comma-separated lists, and a reference standing for a value of any type.
 */
@RequiredArgsConstructor
class SchemaNodes {

    static final String DRAFT = "https://json-schema.org/draft/2020-12/schema";

    /**
     * Spring's simple duration style ({@code 10s}, a bare number of milliseconds) and ISO-8601 ({@code PT10S}).
     */
    private static final String DURATION = "^[+-]?(\\d+(ns|us|ms|s|m|h|d)?|[pP].+)$";
    private static final Pattern CAMEL_HUMP = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern KEBAB_DASH = Pattern.compile("-([a-z0-9])");

    private final JsonNodeFactory nodes = JsonNodeFactory.instance;
    /**
     * A value resolved when it is bound, such as {@code ${env:NAME}}.
     */
    private final String referencePattern;

    ObjectNode object(@Nullable String description) {
        ObjectNode object = nodes.objectNode();
        object.put("type", "object");
        describe(object, description);
        object.put("additionalProperties", false);
        return object;
    }

    /**
     * Any scalar: YAML reads {@code 8123} as a number, which still binds to a string.
     */
    ObjectNode text(@Nullable String description) {
        ObjectNode text = nodes.objectNode();
        text.putArray("type").add("string").add("number").add("boolean");
        return describe(text, description);
    }

    ObjectNode number(@Nullable String description) {
        return referenceOr(description, typed("number"));
    }

    ObjectNode bool(@Nullable String description) {
        return referenceOr(description, typed("boolean"));
    }

    ObjectNode duration(@Nullable String description) {
        ObjectNode duration = typed("string");
        duration.put("pattern", DURATION);
        return referenceOr(description, duration, typed("integer"));
    }

    ObjectNode oneOf(List<String> values, @Nullable String description) {
        ObjectNode lower = nodes.objectNode();
        values.forEach(value -> lower.withArray("enum").add(value.toLowerCase(Locale.ROOT)));
        ObjectNode upper = nodes.objectNode();
        values.forEach(value -> upper.withArray("enum").add(value.toUpperCase(Locale.ROOT)));
        return referenceOr(description, lower, upper);
    }

    /**
     * Also a comma-separated string, as Spring splits one into a list.
     */
    ObjectNode list(ObjectNode element, @Nullable String description) {
        ObjectNode array = typed("array");
        array.set("items", element);
        ObjectNode schema = nodes.objectNode();
        schema.putArray("anyOf").add(array).add(typed("string"));
        return describe(schema, description);
    }

    ObjectNode map(ObjectNode value, @Nullable String description) {
        ObjectNode map = typed("object");
        map.set("additionalProperties", value);
        return describe(map, description);
    }

    /**
     * Completes the name as given and accepts its other relaxed form, kebab-case or camelCase, without offering it.
     */
    void property(ObjectNode object, String name, ObjectNode schema) {
        object.withObject("properties").set(name, schema);
        String alias = alias(name);
        if (!alias.equals(name)) {
            object.withObject("patternProperties").set("^" + alias + "$", schema);
        }
    }

    void exactProperty(ObjectNode object, String name, ObjectNode schema) {
        object.withObject("properties").set(name, schema);
    }

    void exactRequire(ObjectNode object, String name) {
        object.withArray("required").add(name);
    }

    void require(ObjectNode object, String name) {
        String alias = alias(name);
        if (alias.equals(name)) {
            object.withArray("required").add(name);
            return;
        }
        ObjectNode either = nodes.objectNode();
        either.putArray("anyOf")
                .add(nodes.objectNode().set("required", nodes.arrayNode().add(name)))
                .add(nodes.objectNode().set("required", nodes.arrayNode().add(alias)));
        object.withArray("allOf").add(either);
    }

    ObjectNode typed(String type) {
        return nodes.objectNode().put("type", type);
    }

    private ObjectNode referenceOr(@Nullable String description, ObjectNode... schemas) {
        ObjectNode reference = typed("string");
        reference.put("pattern", referencePattern);
        ObjectNode either = nodes.objectNode();
        ArrayNode alternatives = either.putArray("anyOf");
        Arrays.stream(schemas).forEach(alternatives::add);
        alternatives.add(reference);
        return describe(either, description);
    }

    private static ObjectNode describe(ObjectNode schema, @Nullable String description) {
        if (description != null) {
            schema.put("description", description);
        }
        return schema;
    }

    static String alias(String name) {
        return name.contains("-") ? camel(name) : kebab(name);
    }

    private static String kebab(String name) {
        return CAMEL_HUMP.matcher(name).replaceAll("$1-$2").toLowerCase(Locale.ROOT);
    }

    private static String camel(String name) {
        Matcher dash = KEBAB_DASH.matcher(name);
        return dash.replaceAll(match -> match.group(1).toUpperCase(Locale.ROOT));
    }
}
