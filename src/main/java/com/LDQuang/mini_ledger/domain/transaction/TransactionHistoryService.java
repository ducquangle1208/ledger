package com.LDQuang.mini_ledger.domain.transaction;

import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.LDQuang.mini_ledger.api.transaction.TransactionHistoryItemResponse;
import com.LDQuang.mini_ledger.api.transaction.TransactionHistoryResponse;
import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AccountRepository accountRepository;
    private final TransactionEntryRepository transactionEntryRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    @Transactional(readOnly = true)
    public TransactionHistoryResponse history(Long userId, Long accountId, String encodedCursor, Integer requestedLimit) {
        Account account = requireOwnedAccount(userId, accountId);
        Cursor cursor = decodeCursor(encodedCursor);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        List<TransactionHistoryProjection> rows = cursor.createdAt() == null
                ? transactionEntryRepository.findFirstHistoryPage(account.getId(), limit + 1)
                : transactionEntryRepository.findHistoryAfter(
                        account.getId(), cursor.createdAt(), cursor.entryId(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<TransactionHistoryProjection> page = hasMore ? rows.subList(0, limit) : rows;
        List<TransactionHistoryItemResponse> items = page.stream().map(this::toResponse).toList();
        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        return new TransactionHistoryResponse(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public LedgerTransaction getOwnedTransaction(Long userId, Long transactionId) {
        if (!ledgerTransactionRepository.existsOwnedByUser(transactionId, userId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND,
                    "Transaction not found", Map.of("transactionId", transactionId));
        }
        return ledgerTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    private Account requireOwnedAccount(Long userId, Long accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found", Map.of("accountId", accountId)));
    }

    private TransactionHistoryItemResponse toResponse(TransactionHistoryProjection row) {
        String direction = "DEBIT".equals(row.getEntryType()) ? "OUT" : "IN";
        return new TransactionHistoryItemResponse(
                row.getTransactionId(), row.getTransactionType(), row.getTransactionStatus(), direction,
                row.getAmount(), row.getCurrency().trim(), row.getDescription(), row.getBalanceAfter(),
                mask(row.getCounterpartyAccountNumber()), row.getCreatedAt());
    }

    private String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.startsWith("SYSTEM_")) {
            return "DEMO FAUCET";
        }
        return accountNumber.length() <= 4
                ? accountNumber
                : "•••• " + accountNumber.substring(accountNumber.length() - 4);
    }

    private Cursor decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return new Cursor(null, null);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Timestamp.from(Instant.parse(parts[0])), Long.valueOf(parts[1]));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Invalid transaction history cursor");
        }
    }

    private String encodeCursor(TransactionHistoryProjection row) {
        String raw = row.getCreatedAt() + "|" + row.getEntryId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record Cursor(Timestamp createdAt, Long entryId) {
    }
}
