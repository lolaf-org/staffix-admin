package org.lolaf.staffix.admin.server.secrets;

import org.lolaf.staffix.admin.connector.SecretResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

@Configuration
class SecretsConfiguration {

    @Bean
    SecretResolver envSecretResolver() {
        return new EnvSecretResolver(System::getenv);
    }

    @Bean
    SecretResolver fileSecretResolver() {
        return new FileSecretResolver();
    }

    @Bean
    SecretCipher secretCipher(SecretsProperties properties) {
        if (properties.getMasterKey() != null && properties.getMasterKeyFile() != null) {
            throw new IllegalStateException("Give staffix.admin.secrets.master-key or master-key-file, not both");
        }
        String masterKey = properties.getMasterKey();
        if (properties.getMasterKeyFile() != null) {
            try {
                masterKey = Files.readString(properties.getMasterKeyFile());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the master key file " + properties.getMasterKeyFile(), e);
            }
        }
        return new SecretCipher(masterKey, properties.getPreviousMasterKeys());
    }
}
