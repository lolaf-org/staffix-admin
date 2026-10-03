package org.lolaf.staffix.admin.server.connectors;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lolaf.staffix.admin.server.secrets.EnvSecretResolver;
import org.lolaf.staffix.admin.server.secrets.FileSecretResolver;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsBinderTest {

    private static final SecretCipher CIPHER = new SecretCipher(
            Base64.getEncoder().encodeToString("alpha-master-key-of-32-bytes-abc".getBytes(StandardCharsets.UTF_8)), List.of());

    private final SettingsBinder binder = new SettingsBinder(
            List.of(new EnvSecretResolver(Map.of("ALPHA_TENANT", "alpha-tenant")::get), new FileSecretResolver()), CIPHER);

    @TempDir
    Path dir;

    @Test
    void bindsLikeSpringProperties() {
        AlphaSettings settings = binder.bind(Map.of(
                "url", "http://alpha:3100",
                "timeout", "3s",
                "timerBaseUnit", "microseconds",
                "indexed-labels", List.of("fix_eid", "fix_sg")), AlphaSettings.class);

        assertThat(settings).isEqualTo(new AlphaSettings(URI.create("http://alpha:3100"), Duration.ofSeconds(3),
                TimeUnit.MICROSECONDS, Set.of("fix_eid", "fix_sg"), null));
    }

    @Test
    void appliesTheDefaults() {
        assertThat(binder.bind(Map.of(), AlphaSettings.class))
                .isEqualTo(new AlphaSettings(null, Duration.ofSeconds(10), TimeUnit.MILLISECONDS, Set.of(), null));
    }

    @Test
    void resolvesEnvironmentReferences() {
        assertThat(binder.bind(Map.of("tenant", "${env:ALPHA_TENANT}-1"), AlphaSettings.class).getTenant()).isEqualTo("alpha-tenant-1");
    }

    @Test
    void refusesAnUnsetEnvironmentVariable() {
        assertThatThrownBy(() -> binder.bind(Map.of("tenant", "${env:BETA_TENANT}"), AlphaSettings.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("environment variable BETA_TENANT is not set");
    }

    @Test
    void resolvesFileReferencesWithoutTheTrailingNewline() throws IOException {
        Path secret = Files.writeString(dir.resolve("tenant"), "beta-tenant\n");

        assertThat(binder.bind(Map.of("tenant", "${file:" + secret + "}"), AlphaSettings.class).getTenant()).isEqualTo("beta-tenant");
    }

    @Test
    void refusesAnUnknownReferenceScheme() {
        assertThatThrownBy(() -> binder.bind(Map.of("tenant", "${vault:secret/loki}"), AlphaSettings.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown reference scheme vault, known: env, file");
    }

    @Test
    void decryptsEncryptedValues() {
        assertThat(binder.bind(Map.of("tenant", CIPHER.encrypt("gamma-tenant")), AlphaSettings.class).getTenant()).isEqualTo("gamma-tenant");
    }

    @Test
    void refusesAnUnknownSetting() {
        assertThatThrownBy(() -> binder.bind(Map.of("tenat", "alpha"), AlphaSettings.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown setting tenat");
    }

    @Test
    void namesTheSettingThatCannotBeConverted() {
        assertThatThrownBy(() -> binder.bind(Map.of("timeout", "soon"), AlphaSettings.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("setting timeout: ");
    }

    @Value
    static class AlphaSettings {
        @Nullable
        URI url;
        Duration timeout;
        TimeUnit timerBaseUnit;
        Set<String> indexedLabels;
        @Nullable
        String tenant;

        AlphaSettings(@Nullable URI url, @DefaultValue("10s") Duration timeout, @DefaultValue("milliseconds") TimeUnit timerBaseUnit,
                      @DefaultValue Set<String> indexedLabels, @Nullable String tenant) {
            this.url = url;
            this.timeout = timeout;
            this.timerBaseUnit = timerBaseUnit;
            this.indexedLabels = indexedLabels;
            this.tenant = tenant;
        }
    }
}
