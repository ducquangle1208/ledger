package com.LDQuang.mini_ledger.api.user;

import com.LDQuang.mini_ledger.api.account.AccountResponse;
import com.LDQuang.mini_ledger.domain.account.AccountService;
import com.LDQuang.mini_ledger.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    @GetMapping
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(userService.getById(userId(authentication)));
    }

    @GetMapping("/accounts")
    public List<AccountResponse> listAccounts(Authentication authentication) {
        return accountService.listByUser(userId(authentication)).stream()
                .map(AccountResponse::from)
                .toList();
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
