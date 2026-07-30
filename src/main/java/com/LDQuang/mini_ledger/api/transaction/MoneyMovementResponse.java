package com.LDQuang.mini_ledger.api.transaction;

import com.LDQuang.mini_ledger.domain.transaction.LedgerTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record MoneyMovementResponse(
        Long transactionId,
        String referenceCode,
        String type,
        String status,
        BigDecimal amount,
        String currency,
        String description,
        Long debitAccountId,
        BigDecimal debitBalanceAfter,
        Long creditAccountId,
        BigDecimal creditBalanceAfter,
        Instant createdAt,
        Instant completedAt,
        boolean replayed
) {
    public static MoneyMovementResponse created(LedgerTransaction transaction,
                                                Long debitAccountId, BigDecimal debitBalanceAfter,
                                                Long creditAccountId, BigDecimal creditBalanceAfter) {
        return new MoneyMovementResponse(
                transaction.getId(),
                transaction.getReferenceCode(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getAmount(),
                transaction.getCurrency().trim(),
                transaction.getDescription(),
                debitAccountId,
                debitBalanceAfter,
                creditAccountId,
                creditBalanceAfter,
                transaction.getCreatedAt(),
                transaction.getCompletedAt(),
                false
        );
    }

    public MoneyMovementResponse asReplayed() {
        return new MoneyMovementResponse(
                transactionId, referenceCode, type, status, amount, currency, description,
                debitAccountId, debitBalanceAfter, creditAccountId, creditBalanceAfter,
                createdAt, completedAt, true
        );
    }
}
