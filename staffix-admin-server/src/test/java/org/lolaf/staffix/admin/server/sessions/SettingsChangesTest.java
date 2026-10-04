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
