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
package org.lolaf.staffix.admin.server.availability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "READER")
class AvailabilityControllerTest {

    private static final Instant TO = Instant.parse("2026-10-09T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givesTheSharesOfEachStepInWholeExports() throws Exception {
        mockMvc.perform(get("/api/availability?engineId=paris-prod&group=acme&sessionId=trading"
                        + "&from=" + TO.minus(1, ChronoUnit.HOURS) + "&to=" + TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("PT15S"))
                .andExpect(jsonPath("$.steps", hasSize(240)))
                .andExpect(jsonPath("$.steps[0].end").value("2026-10-09T11:00:15Z"))
                .andExpect(jsonPath("$.steps[0].shares.loggedIn").value(1.0))
                .andExpect(jsonPath("$.steps[0].shares.noData").value(0.0))
                .andExpect(jsonPath("$.total.loggedIn").value(1.0))
                .andExpect(jsonPath("$.ratio").value(1.0));
    }

    @Test
    void roundsTheStepUpToWholeExports() throws Exception {
        mockMvc.perform(get("/api/availability?engineId=paris-prod&group=acme&sessionId=trading"
                        + "&from=" + TO.minus(10, ChronoUnit.MINUTES) + "&to=" + TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("PT5S"));
    }

    @Test
    void reportsASessionWhoseMetricsAreNotCollected() throws Exception {
        mockMvc.perform(get("/api/availability?engineId=paris-prod&group=alpha&sessionId=nope"
                        + "&from=" + TO.minus(1, ChronoUnit.HOURS) + "&to=" + TO))
                .andExpect(status().isNotFound());
    }
}
