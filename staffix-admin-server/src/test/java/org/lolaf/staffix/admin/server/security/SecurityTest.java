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
package org.lolaf.staffix.admin.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goes through the real JAAS login with the development users.
 */
@ActiveProfiles("mock")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    private static final String LOGOUT_ACME_DROP_COPY = "/api/engines/paris-prod/groups/acme/sessions/drop-copy/logout";
    private static final String ANNOUNCEMENT = """
            {"engineId": "tokyo", "baseUrl": "https://tokyo:8443", "token": "secret"}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsAnonymousApiCalls() throws Exception {
        mockMvc.perform(get("/api/engines")).andExpect(status().isUnauthorized());
    }

    @Test
    void servesTheUiAnonymously() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void rejectsBadCredentials() throws Exception {
        mockMvc.perform(formLogin("/api/login").user("writer").password("wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writerIsAlsoReader() throws Exception {
        MockHttpSession session = login("writer");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(jsonPath("$.username").value("writer"))
                .andExpect(jsonPath("$.roles", contains("READER", "WRITER")));
        mockMvc.perform(post(LOGOUT_ACME_DROP_COPY).session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void readerCannotModify() throws Exception {
        MockHttpSession session = login("reader");

        mockMvc.perform(get("/api/engines").session(session)).andExpect(status().isOk());
        mockMvc.perform(post(LOGOUT_ACME_DROP_COPY).session(session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresTheCsrfToken() throws Exception {
        mockMvc.perform(post(LOGOUT_ACME_DROP_COPY).session(login("writer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineCanAnnounceButNotUseTheUi() throws Exception {
        mockMvc.perform(post(SecurityConfig.ANNOUNCE_PATH).with(httpBasic("engine", "engine"))
                        .contentType(MediaType.APPLICATION_JSON).content(ANNOUNCEMENT))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/engines").with(httpBasic("engine", "engine")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operatorCannotAnnounce() throws Exception {
        mockMvc.perform(post(SecurityConfig.ANNOUNCE_PATH).with(httpBasic("writer", "writer"))
                        .contentType(MediaType.APPLICATION_JSON).content(ANNOUNCEMENT))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String user) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/api/login").user(user).password(user))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession();
    }
}
