package com.LDQuang.mini_ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserAccountFlowTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void registerCreateAccountAndReadBalance() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "alice-flow-" + suffix;
        MvcResult user = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s@example.com",
                                  "password": "Secret123!"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) user.getRequest().getSession(false);
        long userId = Long.parseLong(user.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

        MvcResult account = mockMvc.perform(post("/api/v1/accounts")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency": "VND"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.accountNumber", startsWith("ML")))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        long accountId = Long.parseLong(account.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.accountNumber", startsWith("ML")))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.balance").value(0.00));
    }
}
