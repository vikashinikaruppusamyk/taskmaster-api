package com.vikashini.taskmaster.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: leave a field out to keep its current value. An empty bio clears it.
 */
public record UpdateProfileRequest(
        @Size(max = 100, message = "must be at most 100 characters")
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
        String name,

        @Size(max = 500, message = "must be at most 500 characters")
        String bio) {
}
