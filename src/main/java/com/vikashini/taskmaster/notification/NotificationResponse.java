package com.vikashini.taskmaster.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        Long taskId,
        Long teamId,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getTaskId(),
                n.getTeamId(), n.isRead(), n.getCreatedAt());
    }
}
