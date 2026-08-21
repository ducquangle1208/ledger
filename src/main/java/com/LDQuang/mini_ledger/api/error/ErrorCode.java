package com.LDQuang.mini_ledger.api.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(400, "Validation failed"),
    SAME_ACCOUNT(400, "Source and destination accounts must be different"),

    // 401 / 403
    UNAUTHORIZED(401, "Authentication is required"),
    INVALID_CREDENTIALS(401, "Invalid username, email, or password"),
    FORBIDDEN(403, "You do not have permission to access this resource"),

    // 404
    USER_NOT_FOUND(404, "User not found"),
    ACCOUNT_NOT_FOUND(404, "Account not found"),
    TRANSACTION_NOT_FOUND(404, "Transaction not found"),

    // 409
    DUPLICATE_USERNAME(409, "Username already exists"),
    DUPLICATE_EMAIL(409, "Email already exists"),
    ACCOUNT_INACTIVE(409, "Account is not active"),
    ACCOUNT_LIMIT_REACHED(409, "Account limit reached"),
    CURRENCY_MISMATCH(409, "Currency mismatch"),
    INSUFFICIENT_FUNDS(409, "Insufficient funds"),
    IDEMPOTENCY_CONFLICT(409, "Idempotency key conflict"),
    IDEMPOTENCY_IN_PROGRESS(409, "Idempotency request is in progress"),
    FAUCET_LIMIT_REACHED(409, "Demo funds have already been claimed today"),
    DATA_INTEGRITY_VIOLATION(409, "Request conflicts with persisted data"),

    // 429
    RATE_LIMITED(429, "Too many requests"),

    // 500
    INTERNAL_ERROR(500, "Internal server error");

    private final int httpStatus;
    private final String defaultMessage;
}
