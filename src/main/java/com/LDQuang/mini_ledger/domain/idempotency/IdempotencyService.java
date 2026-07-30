package com.LDQuang.mini_ledger.domain.idempotency;

import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> IdempotencyResult<T> reserveOrReplay(String key, String operation,
                                                     Object request, Class<T> responseType) {
        validateKey(key);
        String requestHash = requestHash(operation, request);

        if (idempotencyKeyRepository.reserve(key, requestHash) == 1) {
            return IdempotencyResult.newRequest();
        }

        IdempotencyKey existing = idempotencyKeyRepository.findById(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "Request with idempotency key is still being committed"));

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was already used with a different request",
                    Map.of("idempotencyKey", key));
        }
        if (existing.getResponseBody() == null) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    "Request with this Idempotency-Key is already in progress",
                    Map.of("idempotencyKey", key));
        }

        try {
            return IdempotencyResult.replay(objectMapper.readValue(existing.getResponseBody(), responseType));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Stored idempotency response cannot be read");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String key, Object response) {
        IdempotencyKey entry = idempotencyKeyRepository.findById(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Missing idempotency record"));
        try {
            entry.complete(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Response could not be stored for idempotent replay");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key must contain between 1 and 64 characters");
        }
    }

    private String requestHash(String operation, Object request) {
        try {
            String payload = operation + "|" + objectMapper.writeValueAsString(request);
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Request hash could not be calculated");
        }
    }

    private String toHex(byte[] value) {
        StringBuilder hex = new StringBuilder(value.length * 2);
        for (byte b : value) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    public record IdempotencyResult<T>(boolean replayed, T response) {
        public static <T> IdempotencyResult<T> newRequest() {
            return new IdempotencyResult<>(false, null);
        }

        public static <T> IdempotencyResult<T> newRequest(T response) {
            return new IdempotencyResult<>(false, response);
        }

        public static <T> IdempotencyResult<T> replay(T response) {
            return new IdempotencyResult<>(true, response);
        }
    }
}
