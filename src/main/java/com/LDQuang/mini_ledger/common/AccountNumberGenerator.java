package com.LDQuang.mini_ledger.common;

import java.util.UUID;

public final class AccountNumberGenerator {

    private AccountNumberGenerator() {
    }

    public static String pendingNumber() {
        return "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    }

    public static String accountNumber(Long id) {
        return "ML" + String.format("%010d", id);
    }
}
