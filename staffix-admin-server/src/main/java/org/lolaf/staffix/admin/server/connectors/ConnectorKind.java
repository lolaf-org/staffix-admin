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
import org.lolaf.staffix.admin.connector.Description;
import org.lolaf.staffix.admin.connector.Secret;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a form needs to edit a connector of this kind, read from its settings class: a field per setting, and the
 * defaults of its constructor's {@link DefaultValue}s.
 */
@Value
@Builder
public class ConnectorKind {
    String kind;
    ConnectorRole role;
    @Singular
    List<Setting> settings;

    public static ConnectorKind of(String kind, ConnectorRole role, Class<?> settingsClass) {
        Map<String, Parameter> parameters = Arrays.stream(bindingConstructor(settingsClass).getParameters())
                .collect(Collectors.toMap(Parameter::getName, Function.identity()));
        return ConnectorKind.builder()
                .kind(kind)
                .role(role)
                .settings(Arrays.stream(settingsClass.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .map(field -> Setting.of(field, parameters.get(field.getName())))
                        .toList())
                .build();
    }

    private static Constructor<?> bindingConstructor(Class<?> settingsClass) {
        return Arrays.stream(settingsClass.getConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow(() -> new IllegalStateException(settingsClass + " has no public constructor"));
    }

    @Value
    @Builder
    public static class Setting {
        String name;
        /**
         * The setting's {@link Description}, null without one.
         */
        @Nullable
        String description;
        SettingType type;
        /**
         * Neither nullable nor defaulted.
         */
        boolean required;
        /**
         * Shown as {@code set} or its reference, and written only.
         */
        boolean secret;
        /**
         * As written in the settings, e.g. {@code 10s}; null without one.
         */
        @Nullable
        String defaultValue;
        /**
         * The accepted values of an {@link SettingType#ENUM}, or of each element of a {@link SettingType#LIST} of
         * enums, as written in the settings; empty when any value goes.
         */
        @Singular
        List<String> values;

        static Setting of(Field field, @Nullable Parameter parameter) {
            DefaultValue defaultValue = parameter == null ? null : parameter.getAnnotation(DefaultValue.class);
            boolean nullable = field.getAnnotatedType().isAnnotationPresent(Nullable.class);
            SettingBuilder setting = Setting.builder()
                    .name(field.getName())
                    .description(field.isAnnotationPresent(Description.class) ? field.getAnnotation(Description.class).value() : null)
                    .type(SettingType.of(field.getType()))
                    .required(!nullable && defaultValue == null)
                    .secret(field.isAnnotationPresent(Secret.class))
                    .defaultValue(defaultValue == null || defaultValue.value().length == 0 ? null : String.join(",", defaultValue.value()));
            Class<?> valueType = field.getType().isEnum() ? field.getType() : elementType(field);
            if (valueType != null && valueType.isEnum()) {
                Arrays.stream(valueType.getEnumConstants())
                        .forEach(constant -> setting.value(((Enum<?>) constant).name().toLowerCase(Locale.ROOT)));
            }
            return setting.build();
        }

        private static @Nullable Class<?> elementType(Field field) {
            if (Collection.class.isAssignableFrom(field.getType())
                    && field.getGenericType() instanceof ParameterizedType collection
                    && collection.getActualTypeArguments()[0] instanceof Class<?> element) {
                return element;
            }
            return null;
        }
    }

    public enum SettingType {
        TEXT,
        URL,
        DURATION,
        NUMBER,
        BOOLEAN,
        ENUM,
        LIST;

        static SettingType of(Class<?> type) {
            if (type == URI.class) {
                return URL;
            }
            if (type == Duration.class) {
                return DURATION;
            }
            if (type.isEnum()) {
                return ENUM;
            }
            if (type == boolean.class || type == Boolean.class) {
                return BOOLEAN;
            }
            if (Number.class.isAssignableFrom(type) || type == int.class || type == long.class || type == double.class) {
                return NUMBER;
            }
            if (Collection.class.isAssignableFrom(type)) {
                return LIST;
            }
            return TEXT;
        }
    }
}
