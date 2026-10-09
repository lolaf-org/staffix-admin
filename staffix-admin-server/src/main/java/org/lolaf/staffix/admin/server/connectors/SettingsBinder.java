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

import org.lolaf.staffix.admin.connector.SecretResolver;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Binds a connector's stored settings to its factory's settings class the way Spring binds properties: relaxed names
 * ({@code timerBaseUnit} or {@code timer-base-unit}), durations such as {@code 10s}, lists, and the class's defaults.
 * A setting the class does not have is an error, so a misspelled one is not silently ignored. A {@code ${scheme:...}}
 * reference is resolved by the resolver of its scheme, and an {@code enc:v1:} value decrypted.
 */
class SettingsBinder {

    private static final String ROOT = "settings";
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([a-z][a-z0-9-]*):([^}]*)}");

    private final Map<String, SecretResolver> resolvers;
    private final SecretCipher cipher;

    SettingsBinder(List<SecretResolver> resolvers, SecretCipher cipher) {
        this.resolvers = resolvers.stream().collect(Collectors.toUnmodifiableMap(SecretResolver::getScheme, Function.identity()));
        this.cipher = cipher;
    }

    static boolean holdsReference(String value) {
        return REFERENCE.matcher(value).find();
    }

    /**
     * @throws IllegalArgumentException if a setting is unknown, cannot be converted, or its reference or encrypted
     *                                  value cannot be resolved
     */
    <S> S bind(Map<String, Object> settings, Class<S> settingsClass) {
        Map<String, Object> flat = new HashMap<>();
        settings.forEach((name, value) -> flatten(ROOT + "." + name, value, flat));
        try {
            return new Binder(new MapConfigurationPropertySource(flat))
                    .bindOrCreate(ROOT, Bindable.of(settingsClass), new NoUnboundElementsBindHandler(BindHandler.DEFAULT));
        } catch (BindException e) {
            throw new IllegalArgumentException(problem(e), e);
        }
    }

    private void flatten(String name, Object value, Map<String, Object> flat) {
        switch (value) {
            case Map<?, ?> map -> map.forEach((key, element) -> flatten(name + "." + key, element, flat));
            case List<?> list -> {
                for (int i = 0; i < list.size(); i++) {
                    flatten(name + "[" + i + "]", list.get(i), flat);
                }
            }
            case String text -> flat.put(name, resolve(text));
            default -> flat.put(name, value);
        }
    }

    private String resolve(String text) {
        if (SecretCipher.isEncrypted(text)) {
            return cipher.decrypt(text);
        }
        Matcher reference = REFERENCE.matcher(text);
        StringBuilder resolved = new StringBuilder();
        while (reference.find()) {
            SecretResolver resolver = resolvers.get(reference.group(1));
            if (resolver == null) {
                throw new IllegalArgumentException("unknown reference scheme " + reference.group(1) + ", known: "
                        + String.join(", ", new TreeSet<>(resolvers.keySet())));
            }
            reference.appendReplacement(resolved, Matcher.quoteReplacement(resolver.resolve(reference.group(2))));
        }
        return reference.appendTail(resolved).toString();
    }

    private static String problem(BindException e) {
        Throwable cause = e.getCause();
        if (cause instanceof UnboundConfigurationPropertiesException unbound) {
            return unbound.getUnboundProperties().stream()
                    .map(property -> property.getName().toString().substring(ROOT.length() + 1))
                    .sorted()
                    .collect(Collectors.joining(", ", "unknown setting ", ""));
        }
        String setting = e.getProperty() != null ? e.getProperty().getName().toString().substring(ROOT.length() + 1) : null;
        String reason = cause instanceof ConversionFailedException failed && failed.getTargetType().getType().isEnum()
                ? notOneOf(failed)
                : cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
        return setting != null ? "setting " + setting + ": " + reason : reason;
    }

    private static String notOneOf(ConversionFailedException failed) {
        return Arrays.stream(failed.getTargetType().getType().getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", ", failed.getValue() + " is not one of ", ""));
    }
}
