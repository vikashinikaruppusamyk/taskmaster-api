package com.vikashini.taskmaster.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A token that was logged out before it expired, identified by its {@code jti} claim. */
@Entity
@Table(name = "revoked_tokens")
@Getter
@NoArgsConstructor
public class RevokedToken {

    @Id
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public RevokedToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }
}
