package org.lolaf.staffix.admin.server.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NullabilityModelConverterTest {

    private static final String ENGINE = "$.components.schemas.Engine";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresNonNullableFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ENGINE + ".required").value(containsInAnyOrder(
                        "engineId", "reachable", "lastAnnouncedAt")));
    }

    @Test
    void acceptsNullOnNullableFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(ENGINE + ".properties.staffixVersion.type").value(
                        containsInAnyOrder("string", "null")));
    }
}
