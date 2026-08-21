package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.LDQuang.mini_ledger.api.error.ErrorResponse;
import com.LDQuang.mini_ledger.api.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorHandlingTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void missingIdempotencyKeyReturnsStandardValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/faucet/claims")
                        .with(user("1"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Required request header is missing"))
                .andExpect(jsonPath("$.path").value("/api/v1/faucet/claims"))
                .andExpect(jsonPath("$.details.header").value("Idempotency-Key"));
    }

    @Test
    void malformedJsonReturnsStandardValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/faucet/claims")
                        .with(user("1"))
                        .with(csrf())
                        .header("Idempotency-Key", "malformed-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": 1,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.path").value("/api/v1/faucet/claims"));
    }

    @Test
    void invalidRequestReturnsStandardValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .with(user("1"))
                        .with(csrf())
                        .header("Idempotency-Key", "invalid-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": null, "recipientAccountNumber": "", "amount": "0.00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/transfers"))
                .andExpect(jsonPath("$.details.fromAccountId").exists())
                .andExpect(jsonPath("$.details.amount").exists())
                .andExpect(jsonPath("$.details.recipientAccountNumber").exists());
    }

    @Test
    void dataIntegrityViolationReturnsSafeConflictError() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");

        ErrorResponse response = new GlobalExceptionHandler()
                .handleDataIntegrityViolation(new DataIntegrityViolationException("database constraint details"), request)
                .getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getCode()).isEqualTo(ErrorCode.DATA_INTEGRITY_VIOLATION.name());
        assertThat(response.getMessage()).isEqualTo(ErrorCode.DATA_INTEGRITY_VIOLATION.getDefaultMessage());
        assertThat(response.getPath()).isEqualTo("/api/v1/users");
        assertThat(response.getMessage()).doesNotContain("database constraint details");
    }
}
