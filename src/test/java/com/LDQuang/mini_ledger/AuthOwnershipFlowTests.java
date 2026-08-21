package com.LDQuang.mini_ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthOwnershipFlowTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registrationCreatesSessionAndOwnershipPreventsIdor() throws Exception {
        SessionUser alice = register("alice-secure");
        SessionUser bob = register("bob-secure");
        long aliceAccount = createAccount(alice);
        long bobAccount = createAccount(bob);

        mockMvc.perform(get("/api/v1/accounts/{id}", aliceAccount).session(alice.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceAccount));

        mockMvc.perform(get("/api/v1/accounts/{id}", bobAccount).session(alice.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void requestsRequireAuthenticationAndCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        SessionUser user = register("csrf-user");
        mockMvc.perform(post("/api/v1/accounts")
                        .session(user.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"VND\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginAndLogoutUseServerSideSession() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "login-" + suffix;
        String password = "Secret123!";
        SessionUser registered = register(username, password);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registered.userId()));

        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private SessionUser register(String prefix) throws Exception {
        return register(prefix + "-" + UUID.randomUUID().toString().substring(0, 8), "Secret123!");
    }

    private SessionUser register(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, password)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new SessionUser(body.path("id").asLong(), (MockHttpSession) result.getRequest().getSession(false));
    }

    private long createAccount(SessionUser user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .session(user.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"VND\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private record SessionUser(long userId, MockHttpSession session) {
    }
}
