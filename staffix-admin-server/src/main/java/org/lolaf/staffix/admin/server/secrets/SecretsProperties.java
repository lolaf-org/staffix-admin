package org.lolaf.staffix.admin.server.secrets;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.util.List;

/**
 * Keys are base64 encoded 32 bytes (AES-256), e.g. from {@code openssl rand -base64 32}.
 */
@Value
@ConfigurationProperties("staffix.admin.secrets")
public class SecretsProperties {
    /**
     * Encrypts the secrets typed in the console; without one, only references can be saved. Usually given as the
     * {@code STAFFIX_ADMIN_SECRETS_MASTERKEY} environment variable.
     */
    @Nullable
    String masterKey;
    /**
     * A file holding the master key, such as a mounted secret, instead of {@link #getMasterKey()}.
     */
    @Nullable
    Path masterKeyFile;
    /**
     * Keys replaced by the current one: they still decrypt, and the next save re-encrypts under the current key.
     */
    List<String> previousMasterKeys;

    public SecretsProperties(@Nullable String masterKey, @Nullable Path masterKeyFile,
                             @DefaultValue List<String> previousMasterKeys) {
        this.masterKey = masterKey;
        this.masterKeyFile = masterKeyFile;
        this.previousMasterKeys = previousMasterKeys;
    }
}
