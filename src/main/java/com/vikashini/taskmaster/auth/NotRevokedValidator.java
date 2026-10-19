package com.vikashini.taskmaster.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rejects tokens that were logged out. Runs after the signature and expiry checks, on
 * every authenticated request; the lookup is a primary-key hit on a small table.
 */
@Component
@RequiredArgsConstructor
public class NotRevokedValidator implements OAuth2TokenValidator<Jwt> {

    private final RevokedTokenRepository revokedTokens;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null) {
            return failure("Token has no id");
        }
        if (revokedTokens.existsById(jti)) {
            return failure("Token has been revoked");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
