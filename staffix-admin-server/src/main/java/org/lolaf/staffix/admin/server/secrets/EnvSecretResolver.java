package org.lolaf.staffix.admin.server.secrets;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.connector.SecretResolver;

import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public class EnvSecretResolver implements SecretResolver {

    private final UnaryOperator<String> environment;

    @Override
    public String getScheme() {
        return "env";
    }

    @Override
    public String resolve(String reference) {
        String value = environment.apply(reference);
        if (value == null) {
            throw new IllegalArgumentException("environment variable " + reference + " is not set");
        }
        return value;
    }
}
