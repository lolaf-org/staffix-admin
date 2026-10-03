package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "WRITER")
class ConnectorsControllerTest {

    @TempDir
    static Path dataDir;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ConnectorStore store;
    @Autowired
    private JsonMapper jsonMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("staffix.admin.data-dir", () -> dataDir.toString());
        registry.add("staffix.admin.secrets.master-key",
                () -> Base64.getEncoder().encodeToString("alpha-master-key-of-32-bytes-abc".getBytes(StandardCharsets.UTF_8)));
    }

    private long version() throws Exception {
        String body = mockMvc.perform(get("/api/connectors")).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).path("version").asLong();
    }

    private static String config(long version, String tenant) {
        return """
                {"version":%d,"connectors":[{"id":"alpha-loki","name":"Alpha Loki","kind":"loki",
                 "settings":{"url":"http://127.0.0.1:1","tenant":"%s"}}],
                 "defaults":{"logs":"alpha-loki"},"engines":{"paris-prod":{"loggers":{"clients-otlp":"none"}}}}
                """.formatted(version, tenant);
    }

    @Test
    void savesASecretEncryptedAndNeverShowsIt() throws Exception {
        mockMvc.perform(put("/api/connectors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config(version(), "alpha-tenant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectors[0].settings.tenant").value("set"))
                .andExpect(jsonPath("$.engines.paris-prod.loggers.clients-otlp").value("none"));
        String stored = (String) store.load().getConnectors().getFirst().getSettings().get("tenant");

        mockMvc.perform(put("/api/connectors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config(version(), "set")))
                .andExpect(status().isOk());

        assertThat(stored).startsWith("enc:v1:");
        assertThat(store.load().getConnectors().getFirst().getSettings()).containsEntry("tenant", stored);
        mockMvc.perform(get("/api/connectors")).andExpect(jsonPath("$.connectors[0].settings.tenant").value("set"));
    }

    @Test
    void refusesASaveBasedOnAnOlderVersion() throws Exception {
        long version = version();
        mockMvc.perform(put("/api/connectors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config(version, "${env:HOME}")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/connectors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config(version, "${env:HOME}")))
                .andExpect(status().isConflict());
    }

    @Test
    void listsEveryProblemOfAnInvalidSave() throws Exception {
        String invalid = """
                {"version":%d,"connectors":[{"id":"alpha","name":"Alpha","kind":"elastic","settings":{}}],
                 "defaults":{"metrics":"missing"},"engines":{}}
                """.formatted(version());

        mockMvc.perform(put("/api/connectors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("connector alpha: unknown kind elastic, known: demo-logs, demo-metrics, loki, prometheus"))
                .andExpect(jsonPath("$.problems[1]").value("the defaults: metrics connector missing is not a metrics connector"));
    }

    @Test
    void refusesALabelTheConsoleDoesNotQueryOn() throws Exception {
        String config = """
                {"version":%d,"connectors":[{"id":"alpha-loki","name":"Alpha Loki","kind":"loki",
                 "settings":{"url":"http://127.0.0.1:1","indexedLabels":["fix_eid","service_name"]}}],"defaults":{},"engines":{}}
                """.formatted(version());

        mockMvc.perform(put("/api/connectors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]").value("connector alpha-loki: setting indexed-labels[1]: service_name is not one of "
                        + "fix_eid, fix_sg, fix_sn, fix_log_type, fix_msg_type"));
    }

    @Test
    @WithMockUser(roles = "READER")
    void letsAReaderViewButNotSave() throws Exception {
        mockMvc.perform(get("/api/connectors")).andExpect(status().isOk());
        mockMvc.perform(put("/api/connectors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config(0, "x")))
                .andExpect(status().isForbidden());
    }

    @Test
    void describesEachKindsSettings() throws Exception {
        mockMvc.perform(get("/api/connectors/kinds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind == 'loki')].role").value(hasItem("LOGS")))
                .andExpect(jsonPath("$[?(@.kind == 'loki')].settings[?(@.name == 'tenant')].secret").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.kind == 'loki')].settings[?(@.name == 'tenant')].description")
                        .value(hasItem("Sent as X-Scope-OrgID to a multi-tenant Loki; leave empty for a single-tenant one.")))
                .andExpect(jsonPath("$[?(@.kind == 'loki')].settings[?(@.name == 'url')].required").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.kind == 'loki')].settings[?(@.name == 'timeout')].defaultValue").value(hasItem("10s")))
                .andExpect(jsonPath("$[?(@.kind == 'loki')].settings[?(@.name == 'indexedLabels')].values[*]").value(hasItem("fix_eid")))
                .andExpect(jsonPath("$[?(@.kind == 'prometheus')].settings[?(@.name == 'timerBaseUnit')].values[*]").value(hasItem("microseconds")));
    }

    @Test
    void listsTheEmittersEachEnginesSessionsUse() throws Exception {
        mockMvc.perform(get("/api/connectors/engines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.engineId == 'paris-prod')].loggers[*].instanceId").value(hasItem("clients-otlp")))
                .andExpect(jsonPath("$[?(@.engineId == 'nyc-uat')].unmonitoredSessions[*]").value(hasItem("uat-client-1.trading")))
                .andExpect(jsonPath("$[?(@.engineId == 'nyc-uat')].loggers[0].exports").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.engineId == 'paris-prod')].loggers[?(@.instanceId == 'clients-otlp')].otlpEndpoint")
                        .value(hasItem("http://otel-clients:4318")));
    }

    @Test
    void reportsABackendTheTestCannotReach() throws Exception {
        mockMvc.perform(post("/api/connectors/test").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"id":"beta-loki","name":"Beta Loki","kind":"loki","settings":{"url":"http://127.0.0.1:1"}}
                        """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Loki at http://127.0.0.1:1/loki/api/v1/labels did not answer"));
    }
}
