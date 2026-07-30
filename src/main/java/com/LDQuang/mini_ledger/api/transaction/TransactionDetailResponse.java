package com.LDQuang.mini_ledger.api.transaction;

import com.LDQuang.mini_ledger.domain.transaction.LedgerTransaction;
import com.LDQuang.mini_ledger.domain.transaction.TransactionEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionDetailResponse(
        Long id,
        String referenceCode,
        String type,
        String status,
        BigDecimal amount,
        String currency,
        String description,
        Instant createdAt,
        Instant completedAt,
        List<TransactionEntryResponse> entries
) {
    public static TransactionDetailResponse from(LedgerTransaction transaction, List<TransactionEntry> entries) {
        return new TransactionDetailResponse(
                transaction.getId(), transaction.getReferenceCode(), transaction.getType().name(),
                transaction.getStatus().name(), transaction.getAmount(), transaction.getCurrency().trim(),
                transaction.getDescription(), transaction.getCreatedAt(), transaction.getCompletedAt(),
                entries.stream().map(TransactionEntryResponse::from).toList());
    }
}
