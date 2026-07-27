package com.LDQuang.mini_ledger.api.user;

import com.LDQuang.mini_ledger.api.account.AccountResponse;
import com.LDQuang.mini_ledger.domain.account.AccountService;
import com.LDQuang.mini_ledger.domain.user.User;
import com.LDQuang.mini_ledger.domain.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping("/{userId}")
    public UserResponse getById(@PathVariable Long userId) {
        return UserResponse.from(userService.getById(userId));
    }

    @GetMapping("/{userId}/accounts")
    public List<AccountResponse> listAccounts(@PathVariable Long userId) {
        return accountService.listByUser(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
