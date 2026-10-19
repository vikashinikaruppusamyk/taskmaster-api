package com.vikashini.taskmaster.auth;

import com.vikashini.taskmaster.auth.dto.TokenResponse;
import com.vikashini.taskmaster.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Issues the signed access tokens handed out at login. */
@Service
@RequiredArgsConstructor
public class TokenService {

    /** Written into every token and required back by the decoder. */
    public static final String ISSUER = "taskmaster-api";

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expiry());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                // A unique id per token, so that logout can revoke exactly this one.
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", expiresAt);
    }
}
