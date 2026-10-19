package com.vikashini.taskmaster.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes stored notifications to connected clients once the change is committed. */
@Component
@RequiredArgsConstructor
class NotificationPushListener {

    private final NotificationStreams streams;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onNotificationCreated(NotificationService.NotificationCreated event) {
        streams.push(event.recipientId(), event.notification());
    }
}
