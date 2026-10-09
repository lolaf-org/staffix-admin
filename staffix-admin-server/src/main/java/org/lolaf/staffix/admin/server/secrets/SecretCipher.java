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

import org.jspecify.annotations.Nullable;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Encrypts secrets typed in the console as {@code enc:v1:} followed by the base64 of a random 12 byte IV and the
 * AES-GCM ciphertext with its 16 byte tag. A value no configured key decrypts must be typed again.
 */
public class SecretCipher {

    public static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    @Nullable
    private final SecretKey current;
    private final List<SecretKey> previous;
    private final SecureRandom random = new SecureRandom();

    /**
     * @throws IllegalArgumentException if a key is not base64 encoded 32 bytes
     */
    public SecretCipher(@Nullable String currentKey, List<String> previousKeys) {
        this.current = currentKey == null ? null : key(currentKey);
        this.previous = previousKeys.stream().map(SecretCipher::key).toList();
    }

    public static boolean isEncrypted(String value) {
        return value.startsWith(PREFIX);
    }

    public boolean canEncrypt() {
        return current != null;
    }

    public String encrypt(String secret) {
        if (current == null) {
            throw new IllegalStateException("No master key is configured");
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is not available", e);
        }
    }

    /**
     * @throws IllegalArgumentException if no configured key decrypts it
     */
    public String decrypt(String encrypted) {
        return new String(open(encrypted, keys()), StandardCharsets.UTF_8);
    }

    /**
     * Whether a save must encrypt it again: it is under a previous key.
     *
     * @throws IllegalArgumentException if no configured key decrypts it
     */
    public boolean isUnderPreviousKey(String encrypted) {
        if (current != null) {
            try {
                open(encrypted, List.of(current));
                return false;
            } catch (IllegalArgumentException notTheCurrentKey) {
            }
        }
        open(encrypted, previous);
        return true;
    }

    private List<SecretKey> keys() {
        List<SecretKey> keys = new ArrayList<>();
        if (current != null) {
            keys.add(current);
        }
        keys.addAll(previous);
        return keys;
    }

    private static byte[] open(String encrypted, List<SecretKey> keys) {
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("an encrypted secret needs a master key, none is configured");
        }
        byte[] sealed;
        try {
            sealed = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("an encrypted secret is malformed");
        }
        if (sealed.length <= IV_LENGTH) {
            throw new IllegalArgumentException("an encrypted secret is malformed");
        }
        for (SecretKey key : keys) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_LENGTH));
                return cipher.doFinal(sealed, IV_LENGTH, sealed.length - IV_LENGTH);
            } catch (AEADBadTagException wrongKey) {
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("AES-GCM is not available", e);
            }
        }
        throw new IllegalArgumentException("an encrypted secret matches no configured master key; type it again");
    }

    private static SecretKey key(String base64) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("A master key must be base64 encoded");
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("A master key must be 32 bytes, base64 encoded, not " + key.length);
        }
        return new SecretKeySpec(key, "AES");
    }
}
