package com.LDQuang.mini_ledger.api.auth;

import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.LDQuang.mini_ledger.api.user.CreateUserRequest;
import com.LDQuang.mini_ledger.api.user.UserResponse;
import com.LDQuang.mini_ledger.domain.user.User;
import com.LDQuang.mini_ledger.domain.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody CreateUserRequest request,
                                                 HttpServletRequest servletRequest) {
        User user = userService.create(request.username(), request.email(), request.password());
        authenticateSession(user, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        User user = userService.findByLogin(request.login())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        authenticateSession(user, servletRequest);
        return UserResponse.from(user);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(userService.getById(Long.valueOf(authentication.getName())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private void authenticateSession(User user, HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getId().toString(), null,
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    public record CsrfResponse(String token, String headerName) {
    }
}
