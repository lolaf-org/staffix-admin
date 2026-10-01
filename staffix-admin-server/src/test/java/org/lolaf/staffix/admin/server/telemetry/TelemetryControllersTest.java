package org.lolaf.staffix.admin.server.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
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

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "READER")
class TelemetryControllersTest {

    private static final Instant TO = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final String LAST_HOUR = "&from=" + TO.minus(1, ChronoUnit.HOURS) + "&to=" + TO;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givesOneSeriesPerMessageTypeAndDirection() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=paris-prod&group=acme&sessionId=trading&kind=READ_LATENCY&quantile=0.99" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].labels.messageType").exists())
                .andExpect(jsonPath("$[0].points", hasSize(241)));
    }

    @Test
    void givesOneSeriesPerSessionForSessionWideKinds() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&kind=ROUND_TRIP_TIME" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].labels.group", hasSize(3)));
    }

    @Test
    void rejectsAnInvertedTimeRange() throws Exception {
        mockMvc.perform(get("/api/metrics?engineId=nyc-uat&kind=ROUND_TRIP_TIME&from=" + TO + "&to=" + TO.minusSeconds(60)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersLogsByTypeAndText() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&group=ubs&sessionId=trading&types=OUT&text=35=D&limit=20" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[*].type", everyItem(is("OUT"))))
                .andExpect(jsonPath("$[*].group", everyItem(is("ubs"))))
                .andExpect(jsonPath("$[*].sessionId", everyItem(is("trading"))));
    }

    @Test
    void filtersLogsByMessageType() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&group=ubs&msgTypes=8&msgTypes=0&limit=30" + LAST_HOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(30)))
                .andExpect(jsonPath("$[*].line", everyItem(matchesPattern(".*\\|35=(8|0)\\|.*"))));
    }

    @Test
    void needsTheGroupOfASession() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&sessionId=trading" + LAST_HOUR))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownSession() throws Exception {
        mockMvc.perform(get("/api/logs?engineId=paris-prod&group=ubs&sessionId=nope" + LAST_HOUR))
                .andExpect(status().isNotFound());
    }
}
