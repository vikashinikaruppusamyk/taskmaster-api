package com.vikashini.taskmaster.attachment;

import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.task.Task;
import com.vikashini.taskmaster.task.TaskAccess;
import com.vikashini.taskmaster.user.User;
import com.vikashini.taskmaster.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Attachments on a task. Anyone who can see the task can list, download and upload;
 * the uploader or the task's creator can delete one.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final int MAX_NAME_LENGTH = 255;

    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final TaskAccess taskAccess;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(Long userId, Long taskId) {
        taskAccess.requireVisible(taskId, userId);
        return attachments.findByTaskIdOrderByCreatedAtAscIdAsc(taskId).stream()
                .map(AttachmentResponse::from).toList();
    }

    @Transactional
    public AttachmentResponse upload(Long userId, Long taskId, MultipartFile file) {
        Task task = taskAccess.requireVisible(taskId, userId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The file is empty");
        }
        User uploader = userService.getById(userId);
        String key = storage.store(file);
        Attachment attachment = attachments.save(new Attachment(task, uploader,
                cleanFileName(file.getOriginalFilename()), contentTypeOf(file), file.getSize(), key));
        return AttachmentResponse.from(attachment);
    }

    @Transactional(readOnly = true)
    public Download download(Long userId, Long taskId, Long attachmentId) {
        Attachment attachment = requireAttachment(userId, taskId, attachmentId);
        return new Download(storage.load(attachment.getStorageKey()), attachment.getFileName(),
                attachment.getContentType(), attachment.getSizeBytes());
    }

    @Transactional
    public void delete(Long userId, Long taskId, Long attachmentId) {
        Attachment attachment = requireAttachment(userId, taskId, attachmentId);
        boolean uploader = attachment.getUploadedBy().getId().equals(userId);
        boolean taskCreator = attachment.getTask().getCreatedBy().getId().equals(userId);
        if (!uploader && !taskCreator) {
            throw ApiException.forbidden("Only the uploader or the task's creator can delete an attachment");
        }
        attachments.delete(attachment);
        storage.deleteAfterCommit(List.of(attachment.getStorageKey()));
    }

    private Attachment requireAttachment(Long userId, Long taskId, Long attachmentId) {
        taskAccess.requireVisible(taskId, userId);
        return attachments.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> ApiException.notFound("Attachment not found"));
    }

    /**
     * Keeps only the last path segment of what the client sent and drops control
     * characters and quotes, so the name is safe to echo in a Content-Disposition header.
     */
    static String cleanFileName(String original) {
        if (original == null) {
            return "file";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}\"]", "")
                .trim();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(name.length() - MAX_NAME_LENGTH) : name;
    }

    private static String contentTypeOf(MultipartFile file) {
        String type = file.getContentType();
        try {
            return type == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : MediaType.parseMediaType(type).toString();
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    public record Download(Resource resource, String fileName, String contentType, long sizeBytes) {
    }
}
