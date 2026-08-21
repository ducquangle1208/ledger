package com.LDQuang.mini_ledger.api.account;

import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request,
                                                  Authentication authentication) {
        Account account = accountService.create(userId(authentication), request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping
    public List<AccountResponse> list(Authentication authentication) {
        return accountService.listByUser(userId(authentication)).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(@PathVariable Long accountId, Authentication authentication) {
        return AccountResponse.from(accountService.getOwnedAccount(userId(authentication), accountId));
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable Long accountId, Authentication authentication) {
        return BalanceResponse.from(accountService.getOwnedAccount(userId(authentication), accountId));
    }

    @GetMapping("/by-number/{accountNumber}")
    public AccountPreviewResponse getByAccountNumber(@PathVariable String accountNumber) {
        return AccountPreviewResponse.from(accountService.getByAccountNumber(accountNumber));
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
