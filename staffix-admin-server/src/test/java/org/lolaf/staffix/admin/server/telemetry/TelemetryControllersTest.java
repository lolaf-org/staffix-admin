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
package org.lolaf.staffix.admin.server.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "READER")
class TelemetryControllersTest {

    private static final Instant TO = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String LAST_HOUR = "&from=" + TO.minus(1, ChronoUnit.HOURS) + "&to=" + TO;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givesOneSeriesPerMessageTypeInTheDirectionTheTimerIsRecorded() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=paris-prod&group=acme&sessionId=trading&meter=messages.read.latency&statistic=QUANTILE"
                        + "&quantile=0.99&splitBy=fix.mt&splitBy=fix.md" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(3)))
                .andExpect(jsonPath("$.series[*].labels['fix.md']", everyItem(is("i"))))
                .andExpect(jsonPath("$.series[0].points", hasSize(241)));
    }

    @Test
    void givesOneSeriesPerSessionForSessionWideKinds() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&meter=session.rtt&statistic=MEAN&splitBy=fix.sg&splitBy=fix.sn" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[*].labels['fix.sg']", hasSize(3)));
    }

    @Test
    void listsTheMetersOfTheScopeCustomOnesIncluded() throws Exception {
        mockMvc.perform(get("/api/metrics/meters?engineId=paris-prod&group=alpha&sessionId=trading"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("messages.read.latency"))
                .andExpect(jsonPath("$[?(@.name == 'orders.pricing')].custom").value(true))
                .andExpect(jsonPath("$[?(@.name == 'session.clock.offset')].unit").value("NANOSECONDS"));
        mockMvc.perform(get("/api/metrics/meters?engineId=nyc-uat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void splitsACustomTimerByItsOwnTags() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=paris-prod&group=alpha&sessionId=trading&meter=orders.pricing&statistic=QUANTILE&splitBy=venue" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[*].labels.venue", hasSize(2)));
    }

    @Test
    void reportsTheRateWindowAskedFor() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&meter=session.rtt&statistic=MEAN&rateWindow=PT20S" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateWindow").value("PT20S"));
    }

    @Test
    void ratesOverTheStepByDefault() throws Exception {
        mockMvc.perform(get("/api/metrics/messages?engineId=nyc-uat" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateWindow").value("PT15S"));
    }

    @Test
    void widensARateWindowToTwoOfTheEnginesExports() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=paris-prod&group=acme&sessionId=trading&meter=session.rtt&statistic=MEAN&rateWindow=PT2S" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateWindow").value("PT10S"))
                .andExpect(jsonPath("$.minRateWindow").value("PT10S"))
                .andExpect(jsonPath("$.step").value("PT15S"));
    }

    @Test
    void reportsNoRateWindowForAGauge() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&meter=session.clock.offset&statistic=VALUE&gaugeUnit=NANOSECONDS&rateWindow=PT20S" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateWindow").doesNotExist());
    }

    @Test
    void rejectsANonPositiveRateWindow() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&meter=session.rtt&statistic=MEAN&rateWindow=PT0S" + LAST_HOUR))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAShareWithoutItsValue() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&meter=session.logon.status&statistic=SHARE" + LAST_HOUR))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnInvertedTimeRange() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&meter=session.rtt&statistic=MEAN&from=" + TO + "&to=" + TO.minusSeconds(60)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersLogsByTypeAndText() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&group=alpha&sessionId=trading&types=OUT&text=35=D&limit=20" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(20)))
                .andExpect(jsonPath("$.entries[*].type", everyItem(is("OUT"))))
                .andExpect(jsonPath("$.entries[*].group", everyItem(is("alpha"))))
                .andExpect(jsonPath("$.entries[*].sessionId", everyItem(is("trading"))));
    }

    @Test
    void filtersLogsByMessageType() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&group=alpha&msgTypes=8&msgTypes=0&limit=30" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(30)))
                .andExpect(jsonPath("$.entries[*].line", everyItem(matchesPattern(".*\\|35=(8|0)\\|.*"))));
    }

    @Test
    void filtersLogsByFieldValue() throws Exception {
        String order = "ORD-" + (TO.getEpochSecond() - 10);
        mockMvc.perform(get("/api/logs?engineId=paris-prod&group=alpha&sessionId=trading&fieldValues=" + order + "&fieldValues=NONE&limit=30" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[*].line", everyItem(matchesPattern(".*\\|11=" + order + "\\|.*"))));
    }

    @Test
    void needsTheGroupOfASession() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&sessionId=trading" + LAST_HOUR))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownSession() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&group=alpha&sessionId=nope" + LAST_HOUR))
                .andExpect(status().isNotFound());
    }
}
