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
