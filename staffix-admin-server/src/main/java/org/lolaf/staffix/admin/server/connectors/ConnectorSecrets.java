package org.lolaf.staffix.admin.server.connectors;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.secrets.Secret;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.lolaf.staffix.admin.store.Connector;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@link Secret} settings of a connector: kept as a reference, or encrypted before they are stored.
 */
@RequiredArgsConstructor
class ConnectorSecrets {

    /**
     * What the API and the audit show of a secret that is not a reference.
     */
    static final String SET = "set";

    private final SecretCipher cipher;

    /**
     * Encrypts plain values, and re-encrypts those under a previous master key; a plain value without a master key
     * is a problem.
     */
    Connector protect(Connector connector, Class<?> settingsClass, List<String> problems) {
        return replaceSecrets(connector, settingsClass, (name, value) -> {
            if (SettingsBinder.holdsReference(value)) {
                return value;
            }
            if (SecretCipher.isEncrypted(value)) {
                try {
                    return cipher.isUnderPreviousKey(value) ? cipher.encrypt(cipher.decrypt(value)) : value;
                } catch (IllegalArgumentException e) {
                    problems.add("connector " + connector.getId() + ": secret " + name + ": " + e.getMessage());
                    return value;
                }
            }
            if (!cipher.canEncrypt()) {
                problems.add("connector " + connector.getId() + ": secret " + name
                        + " must be a reference such as ${env:NAME}, no master key is configured to encrypt it");
                return value;
            }
            return cipher.encrypt(value);
        });
    }

    /**
     * For the API and the audit: a reference shows as written, any other value as {@link #SET}.
     */
    Connector redact(Connector connector, Class<?> settingsClass) {
        return replaceSecrets(connector, settingsClass, (name, value) -> SettingsBinder.holdsReference(value) ? value : SET);
    }

    private static Connector replaceSecrets(Connector connector, Class<?> settingsClass, SecretReplacement replacement) {
        Set<String> secrets = Arrays.stream(settingsClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Secret.class))
                .map(field -> canonical(field.getName()))
                .collect(Collectors.toSet());
        if (secrets.isEmpty()) {
            return connector;
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        connector.getSettings().forEach((name, value) -> settings.put(name,
                value instanceof String text && secrets.contains(canonical(name)) ? replacement.apply(name, text) : value));
        return connector.toBuilder().clearSettings().settings(settings).build();
    }

    // settings bind with relaxed names: apiToken, api-token and api_token are one setting
    private static String canonical(String name) {
        return name.replace("-", "").replace("_", "").toLowerCase();
    }

    private interface SecretReplacement {
        String apply(String name, String value);
    }
}
