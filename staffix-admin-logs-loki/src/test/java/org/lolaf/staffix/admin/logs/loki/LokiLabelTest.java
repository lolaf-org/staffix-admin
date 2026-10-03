package org.lolaf.staffix.admin.logs.loki;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LokiLabelTest {

    @Test
    void namesEachConstantAfterTheLabelLokiGivesItsAttribute() {
        assertThat(Arrays.stream(LokiLabel.values()))
                .allSatisfy(label -> assertThat(label.label()).isEqualTo(label.name().toLowerCase(Locale.ROOT)));
        assertThat(LokiLabel.FIX_LOG_TYPE.label()).isEqualTo("fix_log_type");
    }
}
