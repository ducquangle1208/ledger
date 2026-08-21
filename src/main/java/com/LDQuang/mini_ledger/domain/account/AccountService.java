package com.LDQuang.mini_ledger.domain.account;

import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.LDQuang.mini_ledger.common.AccountNumberGenerator;
import com.LDQuang.mini_ledger.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String DEFAULT_CURRENCY = "VND";
    private static final int MAX_ACCOUNTS_PER_USER = 3;

    private final AccountRepository accountRepository;
    private final UserService userService;

    @Transactional
    public Account create(Long userId, String currency) {
        userService.getById(userId);
        if (accountRepository.countByUserId(userId) >= MAX_ACCOUNTS_PER_USER) {
            throw new BusinessException(ErrorCode.ACCOUNT_LIMIT_REACHED,
                    "A demo user can have at most " + MAX_ACCOUNTS_PER_USER + " accounts",
                    Map.of("limit", MAX_ACCOUNTS_PER_USER));
        }

        String normalizedCurrency = normalizeCurrency(currency);
        if (!DEFAULT_CURRENCY.equals(normalizedCurrency)) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH,
                    "The public demo currently supports VND only",
                    Map.of("supportedCurrencies", List.of(DEFAULT_CURRENCY)));
        }
        Account account = new Account(userId, AccountNumberGenerator.pendingNumber(), normalizedCurrency);
        Account saved = accountRepository.saveAndFlush(account);
        saved.assignAccountNumber(AccountNumberGenerator.accountNumber(saved.getId()));
        return accountRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public Account getById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account with id " + id + " not found",
                        Map.of("accountId", id)));
    }

    @Transactional(readOnly = true)
    public Account getOwnedAccount(Long userId, Long accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found",
                        Map.of("accountId", accountId)));
    }

    @Transactional(readOnly = true)
    public Account getByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(normalizeAccountNumber(accountNumber))
                .filter(account -> !account.getAccountNumber().startsWith("SYSTEM_"))
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account with number " + accountNumber + " not found",
                        Map.of("accountNumber", accountNumber)));
    }

    @Transactional(readOnly = true)
    public List<Account> listByUser(Long userId) {
        userService.getById(userId);
        return accountRepository.findByUserIdOrderByIdAsc(userId);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return currency.trim().toUpperCase();
    }

    private String normalizeAccountNumber(String accountNumber) {
        return accountNumber == null ? "" : accountNumber.trim().toUpperCase();
    }
}
