package com.LDQuang.mini_ledger.api.transaction;

import java.util.List;

public record TransactionHistoryResponse(
        List<TransactionHistoryItemResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
