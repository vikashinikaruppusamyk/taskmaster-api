package com.vikashini.taskmaster.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The open Server-Sent Event connections, per user. A user may have several (two
 * browser tabs, a phone and a laptop); each gets every notification.
 *
 * <p>Connections live in this instance's memory. Running more than one instance would
 * need a shared broker (Redis pub/sub, for example) so that a notification created on
 * one instance reaches a client connected to another.
 */
@Component
public class NotificationStreams {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreams.class);

    /** Clients reconnect automatically when a stream ends, so this only bounds idle connections. */
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final Map<Long, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter open(Long userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        streams.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        // Sends the headers straight away, so the client knows the stream is open.
        trySend(userId, emitter, SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }

    public void push(Long userId, NotificationResponse notification) {
        Set<SseEmitter> emitters = streams.get(userId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            trySend(userId, emitter, SseEmitter.event()
                    .name("notification")
                    .id(notification.id().toString())
                    .data(notification, MediaType.APPLICATION_JSON));
        }
    }

    /**
     * A comment line every 25 seconds keeps proxies from closing idle connections and
     * reveals clients that went away without saying so.
     */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        streams.forEach((userId, emitters) ->
                emitters.forEach(emitter -> trySend(userId, emitter, SseEmitter.event().comment("keep-alive"))));
    }

    int openStreamCount(Long userId) {
        Set<SseEmitter> emitters = streams.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    private void trySend(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // The client disconnected; forget this stream.
            log.debug("Dropping notification stream for user {}: {}", userId, e.getMessage());
            remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
