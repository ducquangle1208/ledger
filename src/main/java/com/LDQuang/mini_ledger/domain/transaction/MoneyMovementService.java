package com.LDQuang.mini_ledger.domain.transaction;

import com.LDQuang.mini_ledger.api.deposit.DepositRequest;
import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.LDQuang.mini_ledger.api.transaction.MoneyMovementResponse;
import com.LDQuang.mini_ledger.api.transfer.TransferRequest;
import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MoneyMovementService {

    private static final String SYSTEM_CASH_ACCOUNT_NUMBER_PREFIX = "SYSTEM_CASH_";

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TransactionEntryRepository transactionEntryRepository;
    private final IdempotencyService idempotencyService;

    @Transactional(rollbackFor = Exception.class)
    public IdempotencyService.IdempotencyResult<MoneyMovementResponse> deposit(
            DepositRequest request, String idempotencyKey) {
        return depositInternal(request, idempotencyKey);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public IdempotencyService.IdempotencyResult<MoneyMovementResponse> depositInternal(
            DepositRequest request, String idempotencyKey) {
        var idempotency = idempotencyService.reserveOrReplay(
                idempotencyKey, "DEPOSIT", request, MoneyMovementResponse.class);
        if (idempotency.replayed()) {
            return idempotency;
        }

        String currency = normalizeCurrency(request.currency());
        Long targetId = requireAccountId(request.accountId());
        Long systemCashId = accountRepository
                .findIdByAccountNumber(SYSTEM_CASH_ACCOUNT_NUMBER_PREFIX + currency)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "System cash account not found for currency " + currency));

        LockedPair pair = lockPair(systemCashId, targetId);
        Account debit = pair.accountFor(systemCashId);
        Account credit = pair.accountFor(targetId);
        validateCurrency(currency, debit, credit);

        debit.debit(request.amount());
        credit.credit(request.amount());

        MoneyMovementResponse response = persistMovement(
                idempotencyKey, TransactionType.DEPOSIT, request.amount(), currency, request.description(),
                debit, credit);
        idempotencyService.complete(idempotencyKey, response);
        return IdempotencyService.IdempotencyResult.newRequest(response);
    }

    @Transactional(rollbackFor = Exception.class)
    public IdempotencyService.IdempotencyResult<MoneyMovementResponse> transfer(
            Long userId, TransferRequest request, String idempotencyKey) {
        Long sourceId = accountRepository.findOwnedId(request.fromAccountId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Source account not found", Map.of("accountId", request.fromAccountId())));
        Long destinationId = accountRepository.findPublicIdByAccountNumber(
                        normalizeAccountNumber(request.recipientAccountNumber()), SYSTEM_CASH_ACCOUNT_NUMBER_PREFIX + "%")
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Recipient account not found",
                        Map.of("accountNumber", request.recipientAccountNumber())));
        if (sourceId.equals(destinationId)) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT,
                    "Source and destination accounts must be different");
        }

        var idempotency = idempotencyService.reserveOrReplay(
                idempotencyKey, "TRANSFER", request, MoneyMovementResponse.class);
        if (idempotency.replayed()) {
            return idempotency;
        }

        LockedPair pair = lockPair(sourceId, destinationId);
        Account debit = pair.accountFor(sourceId);
        Account credit = pair.accountFor(destinationId);
        String currency = normalizeCurrency(debit.getCurrency());
        validateCurrency(currency, debit, credit);

        debit.debit(request.amount());
        credit.credit(request.amount());

        MoneyMovementResponse response = persistMovement(
                idempotencyKey, TransactionType.TRANSFER, request.amount(), currency, request.description(),
                debit, credit);
        idempotencyService.complete(idempotencyKey, response);
        return IdempotencyService.IdempotencyResult.newRequest(response);
    }

    @Transactional(readOnly = true)
    public LedgerTransaction getTransaction(Long transactionId) {
        return ledgerTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction with id " + transactionId + " not found",
                        Map.of("transactionId", transactionId)));
    }

    @Transactional(readOnly = true)
    public java.util.List<TransactionEntry> getEntries(Long transactionId) {
        if (!ledgerTransactionRepository.existsById(transactionId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND,
                    "Transaction with id " + transactionId + " not found",
                    Map.of("transactionId", transactionId));
        }
        return transactionEntryRepository.findByTransactionIdOrderByIdAsc(transactionId);
    }

    private MoneyMovementResponse persistMovement(String referenceCode, TransactionType type,
                                                  BigDecimal amount, String currency, String description,
                                                  Account debit, Account credit) {
        LedgerTransaction transaction = ledgerTransactionRepository.save(
                LedgerTransaction.completed(referenceCode, type, amount, currency, description));
        transactionEntryRepository.save(TransactionEntry.debit(
                transaction, debit.getId(), amount, debit.getBalance()));
        transactionEntryRepository.save(TransactionEntry.credit(
                transaction, credit.getId(), amount, credit.getBalance()));
        return MoneyMovementResponse.created(
                transaction, debit.getId(), debit.getBalance(), credit.getId(), credit.getBalance());
    }

    private Long requireAccountId(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account with id " + id + " not found",
                    Map.of("accountId", id));
        }
        return id;
    }

    private LockedPair lockPair(Long firstAccountId, Long secondAccountId) {
        Long lowerId = Math.min(firstAccountId, secondAccountId);
        Long higherId = Math.max(firstAccountId, secondAccountId);
        Account lower = lockedAccount(lowerId);
        Account higher = lockedAccount(higherId);
        return new LockedPair(lower, higher);
    }

    private Account lockedAccount(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account with id " + id + " not found",
                        Map.of("accountId", id)));
    }

    private void validateCurrency(String currency, Account debit, Account credit) {
        if (!currency.equals(normalizeCurrency(debit.getCurrency()))
                || !currency.equals(normalizeCurrency(credit.getCurrency()))) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH,
                    "Request currency must match both account currencies",
                    Map.of("currency", currency, "debitAccountId", debit.getId(), "creditAccountId", credit.getId()));
        }
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAccountNumber(String accountNumber) {
        return accountNumber.trim().toUpperCase(Locale.ROOT);
    }

    private record LockedPair(Account lower, Account higher) {
        Account accountFor(Long id) {
            return lower.getId().equals(id) ? lower : higher;
        }
    }
}
