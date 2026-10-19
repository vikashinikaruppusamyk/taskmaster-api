package com.vikashini.taskmaster.auth.dto;

import com.vikashini.taskmaster.auth.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String name,

        @ValidPassword
        String password) {

    /** Spaces around a pasted email should not make it invalid. */
    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
