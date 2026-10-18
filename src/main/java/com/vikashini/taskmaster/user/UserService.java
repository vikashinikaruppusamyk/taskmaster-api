package com.vikashini.taskmaster.user;

import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.user.dto.ChangePasswordRequest;
import com.vikashini.taskmaster.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /** Emails are compared without regard to case or surrounding spaces. */
    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Loads the user behind a valid token. A token can outlive its user only if the
     * database was reset, so this is reported as an authentication problem.
     */
    @Transactional(readOnly = true)
    public User getById(Long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("The account for this token no longer exists"));
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return users.findByEmail(normaliseEmail(email))
                .orElseThrow(() -> ApiException.notFound("No user is registered with that email"));
    }

    /** Only the fields present in the request change; a missing field keeps its value. */
    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().isBlank() ? null : request.bio().trim());
        }
        return user;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }
}
