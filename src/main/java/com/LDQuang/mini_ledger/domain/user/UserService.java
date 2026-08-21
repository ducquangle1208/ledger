package com.LDQuang.mini_ledger.domain.user;

import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User create(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME,
                    "Username '" + username + "' already exists",
                    Map.of("username", username));
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL,
                    "Email '" + email + "' already exists",
                    Map.of("email", email));
        }

        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(username, email, hash);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "User with id " + id + " not found",
                        Map.of("userId", id)));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByLogin(String login) {
        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(login, login);
    }
}
