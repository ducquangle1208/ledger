package com.LDQuang.mini_ledger.api.account;

import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        Long userId,
        String accountNumber,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getUserId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getVersion(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
