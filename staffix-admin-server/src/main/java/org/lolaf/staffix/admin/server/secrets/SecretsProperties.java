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
