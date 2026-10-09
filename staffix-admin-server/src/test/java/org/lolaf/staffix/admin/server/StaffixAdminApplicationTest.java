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
package org.lolaf.staffix.admin.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "READER")
class StaffixAdminApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesTheApi() throws Exception {
        mockMvc.perform(get("/api/engines"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    void servesTheUiOnAClientSideRoute() throws Exception {
        mockMvc.perform(get("/engines/some-engine"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"root\">")));
    }

    @Test
    void keepsUnknownApiPathsNotFound() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound());
    }
}
