package com.LDQuang.mini_ledger.api.transaction;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryItemResponse(
        Long transactionId,
        String type,
        String status,
        String direction,
        BigDecimal amount,
        String currency,
        String description,
        BigDecimal balanceAfter,
        String counterpartyAccountNumber,
        Instant createdAt
) {
}
