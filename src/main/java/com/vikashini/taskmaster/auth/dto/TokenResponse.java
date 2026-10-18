package com.vikashini.taskmaster.auth.dto;

import java.time.Instant;

/** Send {@code accessToken} back as {@code Authorization: Bearer <accessToken>}. */
public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {
}
