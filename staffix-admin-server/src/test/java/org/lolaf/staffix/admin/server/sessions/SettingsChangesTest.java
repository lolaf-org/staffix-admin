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
package org.lolaf.staffix.admin.server.sessions;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsChangesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode json(String json) {
        return JSON.readTree(json);
    }

    @Test
    void namesTheChangedFieldsByTheirPathAndNeverTheirValues() {
        JsonNode before = json("""
                {"desiredSessionState":"LOGGED_IN","heartBeatInterval":{"initiatorInterval":"PT10S"},
                 "fixApplicationSessionSettings":{"logon.password":"******","account":"ACC-1"}}""");
        JsonNode after = json("""
                {"desiredSessionState":"LOGGED_OUT","heartBeatInterval":{"initiatorInterval":"PT10S"},
                 "fixApplicationSessionSettings":{"logon.password":"s3cret","account":"ACC-1"},"testingMode":true}""");

        assertThat(SettingsChanges.describe(before, after))
                .isEqualTo("changed desiredSessionState, fixApplicationSessionSettings.logon.password, testingMode")
                .doesNotContain("s3cret", "LOGGED_OUT");
    }

    @Test
    void anUnchangedDocumentSaysSo() {
        JsonNode settings = json("{\"testingMode\":false}");

        assertThat(SettingsChanges.describe(settings, settings)).isEqualTo("no change");
    }
}
