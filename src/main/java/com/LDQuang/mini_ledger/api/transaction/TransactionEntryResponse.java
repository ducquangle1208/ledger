package com.LDQuang.mini_ledger.api.transaction;

import com.LDQuang.mini_ledger.domain.transaction.TransactionEntry;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionEntryResponse(
        Long id,
        Long accountId,
        String entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) {
    public static TransactionEntryResponse from(TransactionEntry entry) {
        return new TransactionEntryResponse(
                entry.getId(), entry.getAccountId(), entry.getEntryType().name(), entry.getAmount(),
                entry.getBalanceAfter(), entry.getCreatedAt());
    }
}
