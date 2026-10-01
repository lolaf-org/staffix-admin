package org.lolaf.staffix.admin.server.sessions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "WRITER")
class SessionsControllerTest {

    private static final String DROPCOPY = "/api/engines/paris-prod/sessions/DROPCOPY";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTheSimulatedEngines() throws Exception {
        mockMvc.perform(get("/api/engines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].instanceId", hasItem("paris-prod")))
                .andExpect(jsonPath("$[0].reachable").value(true));
    }

    @Test
    void groupsSessions() throws Exception {
        mockMvc.perform(get("/api/engines/paris-prod/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].group", hasItem("equities")))
                .andExpect(jsonPath("$[0].sessions[0].health").exists());
    }

    @Test
    void logsOutThenOn() throws Exception {
        mockMvc.perform(post(DROPCOPY + "/logout").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get(DROPCOPY))
                .andExpect(jsonPath("$.summary.desiredState").value("LOGGED_OUT"))
                .andExpect(jsonPath("$.summary.loggedIn").value(false));

        mockMvc.perform(post(DROPCOPY + "/logon").with(csrf())).andExpect(status().isNoContent());
        await().pollInSameThread().untilAsserted(() -> mockMvc.perform(get(DROPCOPY))
                .andExpect(jsonPath("$.summary.loggedIn").value(true)));
    }

    @Test
    void setsSequenceNumbers() throws Exception {
        mockMvc.perform(put("/api/engines/nyc-uat/sessions/UAT-CLIENT-03/seqnums").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incoming\": 100}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/engines/nyc-uat/sessions/UAT-CLIENT-03"))
                .andExpect(jsonPath("$.incomingSeqNum").value(100));
    }

    @Test
    void answersARefusalWithAProblem() throws Exception {
        mockMvc.perform(post(DROPCOPY + "/reset").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"LOGOUT_LOGON_REST_NUM_FLAG\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("initiator")));
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(post(DROPCOPY + "/messages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"\", \"separator\": \"|\", \"possDup\": false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownEngine() throws Exception {
        mockMvc.perform(get("/api/engines/nowhere/sessions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("nowhere")));
    }
}
