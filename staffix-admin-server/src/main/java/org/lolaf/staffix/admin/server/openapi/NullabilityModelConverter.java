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
package org.lolaf.staffix.admin.server.openapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Makes the spec follow JSpecify: every DTO field is required unless annotated {@link Nullable}, in which case it
 * also accepts {@code null}. springdoc ignores JSpecify, which would leave every generated TypeScript field optional.
 */
@Component
public class NullabilityModelConverter implements ModelConverter {

    private static final String DTO_PACKAGE = "org.lolaf.staffix.admin";

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> resolved = chain.next().resolve(type, context, chain);
        Class<?> rawClass = rawClass(type);
        if (resolved == null || rawClass == null || rawClass.isEnum() || !rawClass.getPackageName().startsWith(DTO_PACKAGE)) {
            return resolved;
        }
        Schema<?> model = resolved.get$ref() == null ? resolved : context.getDefinedModels().get(rawClass.getSimpleName());
        if (model != null && model.getProperties() != null) {
            applyNullability(rawClass, model);
        }
        return resolved;
    }

    private static void applyNullability(Class<?> dtoClass, Schema<?> model) {
        Map<String, Schema> properties = model.getProperties();
        for (Field field : dtoClass.getDeclaredFields()) {
            Schema<?> property = properties.get(field.getName());
            if (property == null || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                properties.put(field.getName(), nullable(property));
            } else if (model.getRequired() == null || !model.getRequired().contains(field.getName())) {
                model.addRequiredItem(field.getName());
            }
        }
    }

    private static Schema<?> nullable(Schema<?> property) {
        if (property.get$ref() != null) {
            Schema<?> nullSchema = new Schema<>();
            nullSchema.addType("null");
            return new Schema<>().oneOf(List.of(property, nullSchema));
        }
        if (property.getTypes() != null) {
            property.addType("null");
        }
        return property;
    }

    private static Class<?> rawClass(AnnotatedType type) {
        return type.getType() == null ? null : Json.mapper().constructType(type.getType()).getRawClass();
    }
}
