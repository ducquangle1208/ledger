package com.LDQuang.mini_ledger.api.account;

import com.LDQuang.mini_ledger.domain.account.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(
        Long accountId,
        String accountNumber,
        String currency,
        BigDecimal balance,
        Instant asOf
) {
    public static BalanceResponse from(Account account) {
        return new BalanceResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                Instant.now()
        );
    }
}
