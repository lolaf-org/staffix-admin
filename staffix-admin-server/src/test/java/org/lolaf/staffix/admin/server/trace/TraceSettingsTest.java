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
package org.lolaf.staffix.admin.server.trace;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.store.TraceConfig;
import org.lolaf.staffix.admin.store.TraceField;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceSettingsTest {

    private static final TraceField SECONDARY = TraceField.builder().tag(526).name("SecondaryClOrdID").build();

    @Test
    void defaultsWithoutConfiguration() {
        assertThat(TraceSettings.of(null)).isEqualTo(new TraceSettings(TraceSettings.DEFAULT_LINKING_TAGS, TraceSettings.DEFAULT_KEY_FIELDS));
    }

    @Test
    void defaultsAListLeftOut() {
        TraceSettings settings = TraceSettings.of(TraceConfig.builder().keyFields(List.of(SECONDARY)).build());

        assertThat(settings.getLinkingTags()).isEqualTo(TraceSettings.DEFAULT_LINKING_TAGS);
        assertThat(settings.getKeyFields()).containsExactly(SECONDARY);
    }

    @Test
    void keepsAnEmptyList() {
        assertThat(TraceSettings.of(TraceConfig.builder().linkingTags(List.of()).build()).getLinkingTags()).isEmpty();
    }

    @Test
    void listsEveryProblem() {
        TraceConfig config = TraceConfig.builder()
                .linkingTags(Arrays.asList(11, 0, 11, null))
                .keyFields(List.of(SECONDARY, SECONDARY.toBuilder().name(" ").build()))
                .build();

        assertThatThrownBy(() -> TraceSettings.of(config))
                .isInstanceOfSatisfying(InvalidTraceSettingsException.class, e -> assertThat(e.getProblems()).containsExactly(
                        "linkingTags: 0 is not a tag",
                        "linkingTags: 11 is listed twice",
                        "linkingTags: null is not a tag",
                        "keyFields: 526 is listed twice",
                        "keyFields: tag 526 has no name"));
    }
}
