package com.vikashini.taskmaster.comment;

import com.vikashini.taskmaster.user.dto.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CommentDtos {

    private CommentDtos() {
    }

    public record CommentRequest(
            @NotBlank(message = "is required")
            @Size(max = 2000, message = "must be at most 2000 characters")
            String body) {
    }

    public record CommentResponse(Long id, Long taskId, UserSummary author, String body,
                                  boolean edited, Instant createdAt, Instant updatedAt) {

        static CommentResponse from(Comment comment) {
            return new CommentResponse(comment.getId(), comment.getTask().getId(),
                    UserSummary.from(comment.getAuthor()), comment.getBody(),
                    comment.getUpdatedAt().isAfter(comment.getCreatedAt()),
                    comment.getCreatedAt(), comment.getUpdatedAt());
        }
    }
}
