package org.lolaf.staffix.admin.server.secrets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = key("alpha-master-key-of-32-bytes-abc");
    private static final String OTHER_KEY = key("an-older-master-key-of-32-bytes!");

    private static String key(String thirtyTwoBytes) {
        return Base64.getEncoder().encodeToString(thirtyTwoBytes.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void decryptsWhatItEncryptedWithAFreshIvEachTime() {
        SecretCipher cipher = new SecretCipher(KEY, List.of());

        String first = cipher.encrypt("alpha-tenant");

        assertThat(first).startsWith("enc:v1:").isNotEqualTo(cipher.encrypt("alpha-tenant"));
        assertThat(cipher.decrypt(first)).isEqualTo("alpha-tenant");
    }

    @Test
    void decryptsUnderAPreviousKeyAndSaysSo() {
        String encrypted = new SecretCipher(OTHER_KEY, List.of()).encrypt("alpha-tenant");
        SecretCipher rotated = new SecretCipher(KEY, List.of(OTHER_KEY));

        assertThat(rotated.decrypt(encrypted)).isEqualTo("alpha-tenant");
        assertThat(rotated.isUnderPreviousKey(encrypted)).isTrue();
        assertThat(rotated.isUnderPreviousKey(rotated.encrypt("alpha-tenant"))).isFalse();
    }

    @Test
    void refusesAValueNoKeyDecrypts() {
        String encrypted = new SecretCipher(OTHER_KEY, List.of()).encrypt("alpha-tenant");

        assertThatThrownBy(() -> new SecretCipher(KEY, List.of()).decrypt(encrypted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no configured master key");
        assertThatThrownBy(() -> new SecretCipher(null, List.of()).decrypt(encrypted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("none is configured");
        assertThatThrownBy(() -> new SecretCipher(KEY, List.of()).decrypt("enc:v1:%%"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an encrypted secret is malformed");
    }

    @Test
    void refusesAKeyThatIsNot32Bytes() {
        assertThatThrownBy(() -> new SecretCipher(key("short"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A master key must be 32 bytes, base64 encoded, not 5");
    }
}
