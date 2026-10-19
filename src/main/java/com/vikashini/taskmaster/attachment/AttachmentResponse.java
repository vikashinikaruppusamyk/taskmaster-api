package com.vikashini.taskmaster.attachment;

import com.vikashini.taskmaster.user.dto.UserSummary;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        Long taskId,
        String fileName,
        String contentType,
        long sizeBytes,
        UserSummary uploadedBy,
        Instant createdAt,
        String downloadUrl) {

    static AttachmentResponse from(Attachment a) {
        Long taskId = a.getTask().getId();
        return new AttachmentResponse(a.getId(), taskId, a.getFileName(), a.getContentType(), a.getSizeBytes(),
                UserSummary.from(a.getUploadedBy()), a.getCreatedAt(),
                "/api/tasks/%d/attachments/%d/download".formatted(taskId, a.getId()));
    }
}
