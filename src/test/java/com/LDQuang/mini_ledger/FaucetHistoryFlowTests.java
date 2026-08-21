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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FaucetHistoryFlowTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void faucetIsDailyIdempotentAndHistoryIsCursorPaginated() throws Exception {
        SessionAccount sender = registerWithAccount("history-sender");
        SessionAccount receiver = registerWithAccount("history-receiver");
        String faucetKey = "faucet-" + UUID.randomUUID();

        MvcResult firstClaim = mockMvc.perform(post("/api/v1/faucet/claims")
                        .session(sender.session())
                        .with(csrf())
                        .header("Idempotency-Key", faucetKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":%d}".formatted(sender.accountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100000.00))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();
        long faucetTransactionId = read(firstClaim, "transactionId").asLong();

        mockMvc.perform(post("/api/v1/faucet/claims")
                        .session(sender.session())
                        .with(csrf())
                        .header("Idempotency-Key", faucetKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":%d}".formatted(sender.accountId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(faucetTransactionId))
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(post("/api/v1/faucet/claims")
                        .session(sender.session())
                        .with(csrf())
                        .header("Idempotency-Key", "faucet-second-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":%d}".formatted(sender.accountId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAUCET_LIMIT_REACHED"));

        MvcResult transfer = mockMvc.perform(post("/api/v1/transfers")
                        .session(sender.session())
                        .with(csrf())
                        .header("Idempotency-Key", "transfer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":%d,"recipientAccountNumber":"%s","amount":"25000.00","description":"Demo payment"}
                                """.formatted(sender.accountId(), receiver.accountNumber())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.debitAccountId").value(sender.accountId()))
                .andReturn();
        long transferId = read(transfer, "transactionId").asLong();

        MvcResult pageOne = mockMvc.perform(get("/api/v1/accounts/{id}/transactions", sender.accountId())
                        .session(sender.session())
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transactionId").value(transferId))
                .andExpect(jsonPath("$.items[0].direction").value("OUT"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        String cursor = read(pageOne, "nextCursor").asText();

        MvcResult pageTwo = mockMvc.perform(get("/api/v1/accounts/{id}/transactions", sender.accountId())
                        .session(sender.session())
                        .param("limit", "1")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transactionId").value(faucetTransactionId))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn();
        assertThat(read(pageTwo, "items").size()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/transactions/{id}", transferId).session(receiver.session()))
                .andExpect(status().isOk());
    }

    private SessionAccount registerWithAccount(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "-" + suffix;
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"Secret123!"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        MvcResult account = mockMvc.perform(post("/api/v1/accounts")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"VND\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(account.getResponse().getContentAsString());
        return new SessionAccount(session, body.path("id").asLong(), body.path("accountNumber").asText());
    }

    private JsonNode read(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path(field);
    }

    private record SessionAccount(MockHttpSession session, long accountId, String accountNumber) {
    }
}
