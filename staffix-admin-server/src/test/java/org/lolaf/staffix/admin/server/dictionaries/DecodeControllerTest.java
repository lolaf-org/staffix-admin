package org.lolaf.staffix.admin.server.dictionaries;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "READER")
class DecodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void decodesWithTheDictionaryOfABackupConfig() throws Exception {
        mockMvc.perform(post("/api/messages/decode").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"engineId": "paris-prod", "group": "alpha", "sessionId": "trading-drp",
                                 "message": "8=FIX.4.4|35=8|39=2|10=000|"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msgTypeName").value("ExecutionReport"))
                .andExpect(jsonPath("$.dictionaryIds[0]").value("default-FIX.4.4"))
                .andExpect(jsonPath("$.fields[2].name").value("OrdStatus"))
                .andExpect(jsonPath("$.fields[2].meaning").value("FILLED"));
    }

    @Test
    void listsTheMessageTypesOfTheDictionariesInScope() throws Exception {
        mockMvc.perform(get("/api/messages/types?engineId=paris-prod&group=alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("NewOrderSingle")))
                .andExpect(jsonPath("$[?(@.msgType == 'D')].name").value("NewOrderSingle"))
                .andExpect(jsonPath("$[0].name").value("AdjustedPositionReport"));
    }

    @Test
    void reportsAnUnknownConfig() throws Exception {
        mockMvc.perform(post("/api/messages/decode").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"engineId": "paris-prod", "group": "alpha", "sessionId": "nope", "message": "35=0|"}"""))
                .andExpect(status().isNotFound());
    }
}
