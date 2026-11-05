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
package org.lolaf.staffix.admin.server.ha;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.audit.AuditOutcome;
import org.lolaf.staffix.admin.audit.AuditSink;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "WRITER")
class HaControllerTest {

    private static final String ENGINE = "/api/ha/engines/geneva-a";

    @Autowired
    private MockMvc mockMvc;
    @MockitoSpyBean
    private EngineGateway gateway;
    @MockitoSpyBean
    private AuditSink auditSink;

    @Test
    void aSwitchoverAnswersAndAuditsItsOutcome() throws Exception {
        doReturn(SwitchoverOutcome.NO_FOLLOWER_IN_SYNC).when(gateway).switchover("geneva-a");

        mockMvc.perform(post(ENGINE + "/switchover").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_FOLLOWER_IN_SYNC"));

        verify(auditSink).record(argThat(entry -> entry.getOperation().equals("switchover: NO_FOLLOWER_IN_SYNC")
                && "geneva-a".equals(entry.getEngineId()) && entry.getOutcome() == AuditOutcome.OK));
    }

    @Test
    void aRefusedSwitchoverIsAuditedAsFailed() throws Exception {
        doThrow(GatewayException.rejected("Node 2 does not lead")).when(gateway).switchover("geneva-a");

        mockMvc.perform(post(ENGINE + "/switchover").with(csrf())).andExpect(status().isConflict());

        verify(auditSink).record(argThat(entry -> entry.getOperation().equals("switchover")
                && entry.getOutcome() == AuditOutcome.FAILED));
    }

    @Test
    void maintenanceAndResyncReachTheEngineAndAreAudited() throws Exception {
        doNothing().when(gateway).setCandidate("geneva-a", false);
        doNothing().when(gateway).resync("geneva-a");
        doNothing().when(gateway).resyncSession(SessionRef.of("geneva-a", "acme", "trading"));

        mockMvc.perform(post(ENGINE + "/candidacy").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"candidate\":false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(ENGINE + "/resync").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(post(ENGINE + "/groups/acme/sessions/trading/resync").with(csrf())).andExpect(status().isNoContent());

        verify(gateway).setCandidate("geneva-a", false);
        verify(gateway).resync("geneva-a");
        verify(gateway).resyncSession(SessionRef.of("geneva-a", "acme", "trading"));
        verify(auditSink).record(argThat(entry -> entry.getOperation().equals("start maintenance")));
        verify(auditSink).record(argThat(entry -> entry.getOperation().equals("resync") && "trading".equals(entry.getSessionId())));
    }

    @Test
    void aComponentAlertIsClearedThroughTheEngineAndAudited() throws Exception {
        doNothing().when(gateway).clearComponentAlert("geneva-a", "ha", "ha-followers");

        mockMvc.perform(post("/api/engines/geneva-a/components/ha/probes/ha-followers/clear").with(csrf()))
                .andExpect(status().isNoContent());

        verify(auditSink).record(argThat(entry -> entry.getOperation().equals("clear alert ha-followers of ha")
                && "geneva-a".equals(entry.getEngineId())));
    }

    @Test
    @WithMockUser(roles = "READER")
    void aReaderCannotActOnThePair() throws Exception {
        mockMvc.perform(post(ENGINE + "/switchover").with(csrf())).andExpect(status().isForbidden());

        verify(gateway, never()).switchover("geneva-a");
    }
}
