package com.vikashini.taskmaster.notification;

import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final ApplicationEventPublisher events;

    /**
     * Stores a notification for each recipient except the person who caused it, then
     * hands it to the live stream. The push happens only after the surrounding
     * transaction commits (see {@link NotificationPushListener}), so nobody is told
     * about a change that was rolled back.
     */
    @Transactional
    public void notify(Collection<Long> recipientIds, Long actorId, NotificationType type,
                       String message, Long taskId, Long teamId) {
        Set<Long> recipients = new LinkedHashSet<>(recipientIds);
        recipients.remove(null);
        recipients.remove(actorId);
        for (Long recipientId : recipients) {
            Notification saved = notifications.save(new Notification(recipientId, type, message, taskId, teamId));
            events.publishEvent(new NotificationCreated(recipientId, NotificationResponse.from(saved)));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notifications.findByRecipientIdAndReadFalseOrderByCreatedAtDescIdDesc(userId, pageable)
                : notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return PageResponse.of(page, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notifications.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.setRead(true);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId);
    }

    /** Published once per stored notification; consumed after commit. */
    public record NotificationCreated(Long recipientId, NotificationResponse notification) {
        public NotificationCreated {
            Objects.requireNonNull(recipientId);
            Objects.requireNonNull(notification);
        }
    }
}
