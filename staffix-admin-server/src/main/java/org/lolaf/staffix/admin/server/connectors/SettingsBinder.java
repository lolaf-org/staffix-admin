package org.lolaf.staffix.admin.server.connectors;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Binds a connector's stored settings to its factory's settings class the way Spring binds properties: relaxed names
 * ({@code timerBaseUnit} or {@code timer-base-unit}), durations such as {@code 10s}, lists, and the class's defaults.
 * A setting the class does not have is an error, so a misspelled one is not silently ignored.
 */
class SettingsBinder {

    private static final String ROOT = "settings";
    private static final Pattern ENVIRONMENT_REFERENCE = Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)}");

    private final UnaryOperator<String> environment;

    SettingsBinder(UnaryOperator<String> environment) {
        this.environment = environment;
    }

    /**
     * @throws IllegalArgumentException if a setting is unknown, cannot be converted, or references an environment
     *                                  variable that is not set
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
        Matcher reference = ENVIRONMENT_REFERENCE.matcher(text);
        StringBuilder resolved = new StringBuilder();
        while (reference.find()) {
            String value = environment.apply(reference.group(1));
            if (value == null) {
                throw new IllegalArgumentException("environment variable " + reference.group(1) + " is not set");
            }
            reference.appendReplacement(resolved, Matcher.quoteReplacement(value));
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
        String reason = cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
        return setting != null ? "setting " + setting + ": " + reason : reason;
    }
}
