package org.lolaf.staffix.admin.server.connectors;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsBinderTest {

    private final SettingsBinder binder = new SettingsBinder(Map.of("ALPHA_TENANT", "alpha-tenant")::get);

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
