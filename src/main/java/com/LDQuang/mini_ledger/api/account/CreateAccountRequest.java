package com.LDQuang.mini_ledger.api.account;

import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @Size(min = 3, max = 3) String currency
) {
}
