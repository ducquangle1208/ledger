package com.LDQuang.mini_ledger.api.account;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotNull Long userId,
        @Size(min = 3, max = 3) String currency
) {
}
