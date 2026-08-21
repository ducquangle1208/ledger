package com.LDQuang.mini_ledger.api.account;

import com.LDQuang.mini_ledger.domain.account.Account;

public record AccountPreviewResponse(
        String accountNumber,
        String maskedAccountNumber,
        String currency,
        String status
) {
    public static AccountPreviewResponse from(Account account) {
        String number = account.getAccountNumber();
        String masked = number.length() <= 4 ? number : "•••• " + number.substring(number.length() - 4);
        return new AccountPreviewResponse(number, masked, account.getCurrency().trim(), account.getStatus().name());
    }
}
