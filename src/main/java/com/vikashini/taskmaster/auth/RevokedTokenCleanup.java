package com.vikashini.taskmaster.auth;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Deletes revocations whose tokens have expired anyway, so the table only holds tokens
 * that would otherwise still work.
 */
@Component
@RequiredArgsConstructor
class RevokedTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanup.class);

    private final RevokedTokenRepository revokedTokens;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    @Transactional
    void deleteExpired() {
        int deleted = revokedTokens.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.debug("Removed {} expired token revocations", deleted);
        }
    }
}
