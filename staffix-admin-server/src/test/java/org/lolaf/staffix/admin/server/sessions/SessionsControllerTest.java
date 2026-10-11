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
import org.lolaf.staffix.admin.audit.AuditOutcome;
import org.lolaf.staffix.admin.audit.AuditSink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "WRITER")
class SessionsControllerTest {

    private static final String ACME_TRADING = "/api/engines/paris-prod/groups/acme/sessions/trading";
    private static final String ALPHA_TRADING = "/api/engines/paris-prod/groups/alpha/sessions/trading";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private AuditSink auditSink;

    @Test
    void listsTheSimulatedEngines() throws Exception {
        mockMvc.perform(get("/api/engines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].engineId", hasItem("paris-prod")))
                .andExpect(jsonPath("$[0].reachable").value(true));
    }

    @Test
    void groupsSessions() throws Exception {
        mockMvc.perform(get("/api/engines/paris-prod/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].group", hasItem("alpha")))
                .andExpect(jsonPath("$[0].sessions[0].status").exists())
                .andExpect(jsonPath("$[0].sessions[0].instanceId").exists())
                .andExpect(jsonPath("$[0].sessions[0].identity.target.compId").exists());
    }

    @Test
    void logsOutThenOn() throws Exception {
        mockMvc.perform(post(ACME_TRADING + "/logout").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get(ACME_TRADING))
                .andExpect(jsonPath("$.summary.desiredState").value("LOGGED_OUT"))
                .andExpect(jsonPath("$.summary.status").value("LOGGED_OUT_BY_OPERATOR"));

        mockMvc.perform(post(ACME_TRADING + "/logon").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get(ACME_TRADING)).andExpect(jsonPath("$.summary.status").value("LOGGED_IN"));
    }

    @Test
    void clearsAnAlertByHandAndAuditsTheAttemptWhetherAcceptedOrNot() throws Exception {
        mockMvc.perform(post(ACME_TRADING + "/probes/large-order/clear").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(post(ACME_TRADING + "/probes/session-down/clear").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be cleared by hand")));
        mockMvc.perform(post(ACME_TRADING + "/probes/unknown/clear").with(csrf())).andExpect(status().isNotFound());

        verify(auditSink).record(argThat(entry -> entry.getOperation().equals("clear alert large-order")
                && entry.getOutcome() == AuditOutcome.OK && "acme".equals(entry.getGroup())));
        verify(auditSink).record(argThat(entry -> entry.getOperation().equals("clear alert session-down")
                && entry.getOutcome() == AuditOutcome.FAILED));
    }

    @Test
    @WithMockUser(roles = "READER")
    void aReaderCannotClearAnAlert() throws Exception {
        mockMvc.perform(post(ACME_TRADING + "/probes/large-order/clear").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void setsSequenceNumbers() throws Exception {
        mockMvc.perform(put("/api/engines/nyc-uat/groups/uat-client-3/sessions/trading/seqnums").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incoming\": 100}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/engines/nyc-uat/groups/uat-client-3/sessions/trading"))
                .andExpect(jsonPath("$.incomingSeqNum").value(100));
    }

    @Test
    void answersARefusalWithAProblem() throws Exception {
        mockMvc.perform(post(ACME_TRADING + "/reset").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"LOGOUT_LOGON_RESET_NUM_FLAG\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("initiator")));
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(post(ACME_TRADING + "/messages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\": [\"\"], \"separator\": \"|\", \"possDup\": false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ACME_TRADING + "/messages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\": [], \"separator\": \"|\", \"possDup\": false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saysWhichOfSeveralMessagesWasRefused() throws Exception {
        mockMvc.perform(post(ACME_TRADING + "/messages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\": [\"35=D|11=ORDER-1|55=AAPL\", \"11=ORDER-2|55=AAPL\"], \"separator\": \"|\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", startsWith("Message 2 of 2: ")));
    }

    @Test
    void switchesAnInitiatorToABackup() throws Exception {
        mockMvc.perform(post(ALPHA_TRADING + "/activate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configId\": \"trading-drp\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ALPHA_TRADING))
                .andExpect(jsonPath("$.summary.runningConfigId").value("trading-drp"))
                .andExpect(jsonPath("$.configs[1].id").value("trading-drp"));
        mockMvc.perform(post(ALPHA_TRADING + "/activate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configId\": \"trading\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void reportsAnUnknownEngine() throws Exception {
        mockMvc.perform(get("/api/engines/nowhere/groups"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("nowhere")));
    }

    @Test
    void updatesASessionsSettings() throws Exception {
        String fundBeta = "/api/engines/london-prod/groups/fund-beta/sessions/trading/settings";

        mockMvc.perform(put(fundBeta).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desiredSessionState\": \"LOGGED_OUT\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(fundBeta)).andExpect(jsonPath("$.desiredSessionState").value("LOGGED_OUT"));
    }

    @Test
    void removesAnAcceptorSessionButNotAnInitiators() throws Exception {
        mockMvc.perform(delete("/api/engines/london-prod/groups/fund-alpha/sessions/drop-copy/settings").with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/engines/london-prod/groups/fund-alpha/sessions/drop-copy")).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/engines/london-prod/groups/delta/sessions/market-data/settings").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Only an acceptor session")));
    }

    @Test
    void addsOnlyAcceptorSessions() throws Exception {
        mockMvc.perform(post("/api/engines/london-prod/session-settings-stores/simulated/sessions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fixSessionId\": {\"group\": \"delta\", \"name\": \"new\"}, \"fixSessionType\": \"INITIATOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Only an acceptor session")));
    }

    @Test
    void servesTheEnginesSchemaSettingsStoresAndSessionComponents() throws Exception {
        mockMvc.perform(get("/api/engines/london-prod/schemas/session-settings")).andExpect(status().isOk());
        mockMvc.perform(get("/api/engines/london-prod/session-settings-stores"))
                .andExpect(jsonPath("$[0].instanceId").value("simulated"))
                .andExpect(jsonPath("$[0].persistent").value(false));
        mockMvc.perform(get("/api/engines/london-prod/groups/fund-beta/sessions/trading"))
                .andExpect(jsonPath("$.sessionSettingsStore").value("simulated"));
        mockMvc.perform(get("/api/engines/paris-prod/session-components"))
                .andExpect(jsonPath("$.applicationFactories[0].applicationIds[0]").value("default"))
                .andExpect(jsonPath("$.messagesStores[0]").value("default"))
                .andExpect(jsonPath("$.messagesLoggers").isNotEmpty())
                .andExpect(jsonPath("$.sessionsPlugins[0].pluginTypes[0]").value("org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager"));
    }

    @Test
    @WithMockUser(roles = "READER")
    void aReaderCannotChangeSettings() throws Exception {
        mockMvc.perform(delete("/api/engines/london-prod/groups/fund-beta/sessions/trading/settings").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
