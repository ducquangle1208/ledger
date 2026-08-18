package com.LDQuang.mini_ledger.api.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(400, "Validation failed"),
    SAME_ACCOUNT(400, "Source and destination accounts must be different"),

    // 404
    USER_NOT_FOUND(404, "User not found"),
    ACCOUNT_NOT_FOUND(404, "Account not found"),
    TRANSACTION_NOT_FOUND(404, "Transaction not found"),

    // 409
    DUPLICATE_USERNAME(409, "Username already exists"),
    DUPLICATE_EMAIL(409, "Email already exists"),
    ACCOUNT_INACTIVE(409, "Account is not active"),
    CURRENCY_MISMATCH(409, "Currency mismatch"),
    INSUFFICIENT_FUNDS(409, "Insufficient funds"),
    IDEMPOTENCY_CONFLICT(409, "Idempotency key conflict"),
    IDEMPOTENCY_IN_PROGRESS(409, "Idempotency request is in progress"),
    DATA_INTEGRITY_VIOLATION(409, "Request conflicts with persisted data"),

    // 500
    INTERNAL_ERROR(500, "Internal server error");

    private final int httpStatus;
    private final String defaultMessage;
}
