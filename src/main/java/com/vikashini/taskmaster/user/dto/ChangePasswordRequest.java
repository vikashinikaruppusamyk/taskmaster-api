package com.vikashini.taskmaster.user.dto;

import com.vikashini.taskmaster.auth.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "is required")
        String currentPassword,

        @ValidPassword
        String newPassword) {
}
