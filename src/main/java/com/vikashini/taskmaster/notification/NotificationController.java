package com.vikashini.taskmaster.notification;

import com.vikashini.taskmaster.auth.CurrentUserId;
import com.vikashini.taskmaster.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreams streams;

    @GetMapping
    @Operation(summary = "List my notifications, newest first")
    public PageResponse<NotificationResponse> list(@CurrentUserId Long userId,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.list(userId, unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many of my notifications are unread")
    public Map<String, Long> unreadCount(@CurrentUserId Long userId) {
        return Map.of("unread", notificationService.unreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(@CurrentUserId Long userId, @PathVariable Long id) {
        return notificationService.markRead(userId, id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public Map<String, Integer> markAllRead(@CurrentUserId Long userId) {
        return Map.of("updated", notificationService.markAllRead(userId));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live notifications as Server-Sent Events",
            description = "Each new notification arrives as a 'notification' event whose data is the "
                    + "same JSON as in the list. Send the token in the Authorization header.")
    public SseEmitter stream(@CurrentUserId Long userId) {
        return streams.open(userId);
    }
}
