package com.vikashini.taskmaster.auth;

import com.vikashini.taskmaster.auth.dto.LoginRequest;
import com.vikashini.taskmaster.auth.dto.RegisterRequest;
import com.vikashini.taskmaster.auth.dto.TokenResponse;
import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.user.User;
import com.vikashini.taskmaster.user.UserRepository;
import com.vikashini.taskmaster.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RevokedTokenRepository revokedTokens;

    /**
     * Compared against when the email is unknown, so a failed login takes about as long
     * whether or not the account exists. Otherwise response times would reveal which
     * emails are registered.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       TokenService tokenService, RevokedTokenRepository revokedTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.revokedTokens = revokedTokens;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-password");
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = UserService.normaliseEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User(email, request.name().trim(), passwordEncoder.encode(request.password()));
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same email at the same moment: the unique
            // constraint catches the one that lost the race.
            throw ApiException.conflict("An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // No stored password can be this long (see ValidPassword), and BCrypt would
        // throw on it rather than simply not match.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > ValidPassword.Validator.MAX_BYTES) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        Optional<User> user = users.findByEmail(UserService.normaliseEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        return tokenService.issue(user.get());
    }

    /** Revokes the token used for this request; other sessions stay signed in. */
    @Transactional
    public void logout(Jwt token) {
        revokedTokens.save(new RevokedToken(token.getId(), token.getExpiresAt()));
    }
}
