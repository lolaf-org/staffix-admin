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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The schema of {@code application.yml}, from Spring's configuration metadata. Only {@code staffix.admin} is checked:
 * any other key may be one of Spring Boot's settings.
 */
@UtilityClass
class ApplicationSchema {

    private static final String PREFIX = "staffix.admin.";
    private static final SchemaNodes NODES = new SchemaNodes("\\$\\{[^}]+}");
    private static final Pattern CODE = Pattern.compile("\\{@code ([^}]*)}");
    private static final Pattern LINK = Pattern.compile("\\{@link\\s+([^}\\s]+)(?:\\s+([^}]+))?}");

    /**
     * @param properties the {@code properties} of every {@code spring-configuration-metadata.json}
     */
    static ObjectNode of(List<JsonNode> properties, ClassLoader classLoader) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("$schema", SchemaNodes.DRAFT);
        schema.put("title", "staffix-admin application.yml");
        schema.put("description", "The console's settings. Spring Boot's own, such as server.port, are not checked.");
        schema.put("type", "object");
        ObjectNode staffix = NODES.object(null);
        ObjectNode admin = NODES.object("The console's own settings.");
        NODES.exactProperty(staffix, "admin", admin);
        NODES.exactProperty(schema, "staffix", staffix);
        properties.stream()
                .filter(property -> property.get("name").asString().startsWith(PREFIX))
                .sorted(Comparator.comparing(property -> property.get("name").asString()))
                .forEach(property -> add(admin, property.get("name").asString().substring(PREFIX.length()), property(property, classLoader)));
        return schema;
    }

    private static void add(ObjectNode group, String path, ObjectNode schema) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            NODES.property(group, path, schema);
            return;
        }
        String name = path.substring(0, dot);
        JsonNode existing = group.path("properties").get(name);
        ObjectNode nested;
        if (existing instanceof ObjectNode known) {
            nested = known;
        } else {
            nested = NODES.object(null);
            NODES.property(group, name, nested);
        }
        add(nested, path.substring(dot + 1), schema);
    }

    private static ObjectNode property(JsonNode property, ClassLoader classLoader) {
        String description = property.has("description") ? plain(property.get("description").asString()) : null;
        ObjectNode schema = value(property.get("type").asString(), description, classLoader);
        if (property.has("defaultValue")) {
            schema.set("default", property.get("defaultValue"));
        }
        return schema;
    }

    private static ObjectNode value(String type, @Nullable String description, ClassLoader classLoader) {
        int generic = type.indexOf('<');
        String raw = generic < 0 ? type : type.substring(0, generic);
        List<String> arguments = generic < 0 ? List.of() : arguments(type.substring(generic + 1, type.length() - 1));
        return switch (raw) {
            case "java.time.Duration" -> NODES.duration(description);
            case "boolean", "java.lang.Boolean" -> NODES.bool(description);
            case "int", "long", "double", "float", "java.lang.Integer", "java.lang.Long", "java.lang.Double",
                 "java.lang.Float", "java.math.BigDecimal" -> NODES.number(description);
            case "java.util.List", "java.util.Set", "java.util.Collection" ->
                    NODES.list(value(arguments.getFirst(), null, classLoader), description);
            case "java.util.Map" -> NODES.map(value(arguments.getLast(), null, classLoader), description);
            default -> enumValues(raw, classLoader)
                    .map(values -> NODES.oneOf(values, description))
                    .orElseGet(() -> NODES.text(description));
        };
    }

    private static List<String> arguments(String types) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < types.length(); i++) {
            switch (types.charAt(i)) {
                case '<' -> depth++;
                case '>' -> depth--;
                case ',' -> {
                    if (depth == 0) {
                        arguments.add(types.substring(start, i).trim());
                        start = i + 1;
                    }
                }
                default -> {
                }
            }
        }
        arguments.add(types.substring(start).trim());
        return arguments;
    }

    private static Optional<List<String>> enumValues(String type, ClassLoader classLoader) {
        try {
            Class<?> enumType = Class.forName(type, false, classLoader);
            return enumType.isEnum()
                    ? Optional.of(Arrays.stream(enumType.getEnumConstants()).map(constant -> ((Enum<?>) constant).name()).toList())
                    : Optional.empty();
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * The metadata keeps the javadoc's tags, which an editor shows as they are.
     */
    static String plain(String javadoc) {
        Matcher link = LINK.matcher(CODE.matcher(javadoc).replaceAll("$1"));
        return link.replaceAll(match -> Matcher.quoteReplacement(match.group(2) != null ? match.group(2) : linkText(match.group(1))));
    }

    private static String linkText(String target) {
        String member = target.substring(Math.max(target.lastIndexOf('.'), target.lastIndexOf('#')) + 1).replace("()", "");
        return member.startsWith("get") && member.length() > 3 ? SchemaNodes.alias(Introspector.decapitalize(member.substring(3))) : member;
    }
}
