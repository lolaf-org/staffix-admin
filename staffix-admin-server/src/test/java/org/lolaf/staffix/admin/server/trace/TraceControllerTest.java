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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "READER")
class TraceControllerTest {

    @TempDir
    static Path dataDir;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("staffix.admin.data-dir", () -> dataDir.toString());
    }

    @AfterEach
    void removeConfiguration() throws IOException {
        Files.deleteIfExists(dataDir.resolve("console.yaml"));
    }

    @Test
    void servesTheDefaultsWithoutConfiguration() throws Exception {
        mockMvc.perform(get("/api/trace/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkingTags", contains(11, 41, 37, 131, 117)))
                .andExpect(jsonPath("$.keyFields", hasSize(13)))
                .andExpect(jsonPath("$.keyFields[0].name", is("ClOrdID")));
    }

    @Test
    void servesTheConfiguredSettings() throws Exception {
        Files.writeString(dataDir.resolve("console.yaml"), """
                trace:
                  linkingTags: [11, 526]
                  keyFields:
                    - tag: 526
                      name: SecondaryClOrdID
                """);

        mockMvc.perform(get("/api/trace/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkingTags", contains(11, 526)))
                .andExpect(jsonPath("$.keyFields[*].name", contains("SecondaryClOrdID")));
    }

    @Test
    void reportsInvalidSettingsAsAServerProblem() throws Exception {
        Files.writeString(dataDir.resolve("console.yaml"), """
                trace:
                  linkingTags: [11, 11]
                """);

        mockMvc.perform(get("/api/trace/settings"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.problems", contains("linkingTags: 11 is listed twice")));
    }
}
