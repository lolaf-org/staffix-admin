package org.lolaf.staffix.admin.connector;

/**
 * Resolves the {@code ${scheme:reference}} values of connector settings, e.g. {@code ${env:LOKI_TENANT}}.
 */
public interface SecretResolver {

    /**
     * Lower case, e.g. {@code env}; unique among the console's resolvers.
     */
    String getScheme();

    /**
     * @throws IllegalArgumentException if the reference resolves to nothing; its message must not hold a secret
     */
    String resolve(String reference);
}
