package com.vikashini.taskmaster.user.dto;

import com.vikashini.taskmaster.user.User;

import java.time.Instant;

/** The full profile, shown only to the user it belongs to. */
public record UserResponse(Long id, String email, String name, String bio, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getBio(),
                user.getCreatedAt());
    }
}
