package com.vikashini.taskmaster.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code app.jwt.*}. The secret must be at least 32 characters because HS256 needs a
 * 256-bit key; the application refuses to start with a shorter one.
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotNull @Size(min = 32) String secret,
        @NotNull Duration expiry) {
}
