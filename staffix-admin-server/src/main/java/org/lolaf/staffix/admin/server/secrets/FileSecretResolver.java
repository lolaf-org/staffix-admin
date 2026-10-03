package org.lolaf.staffix.admin.server.secrets;

import org.lolaf.staffix.admin.connector.SecretResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a mounted secret, such as a Kubernetes or Docker secret file. The trailing newline an editor or
 * {@code echo} leaves is not part of the secret.
 */
public class FileSecretResolver implements SecretResolver {

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public String resolve(String reference) {
        try {
            String content = Files.readString(Path.of(reference), StandardCharsets.UTF_8);
            return content.endsWith("\r\n") ? content.substring(0, content.length() - 2)
                    : content.endsWith("\n") ? content.substring(0, content.length() - 1)
                    : content;
        } catch (IOException e) {
            throw new IllegalArgumentException("secret file " + reference + " cannot be read");
        }
    }
}
