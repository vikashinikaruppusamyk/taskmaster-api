package com.vikashini.taskmaster.attachment;

import com.vikashini.taskmaster.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    @Operation(summary = "List a task's attachments")
    public List<AttachmentResponse> list(@CurrentUserId Long userId, @PathVariable Long taskId) {
        return attachmentService.list(userId, taskId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a file (multipart field 'file', up to 10 MB)")
    public AttachmentResponse upload(@CurrentUserId Long userId, @PathVariable Long taskId,
                                     @RequestPart("file") MultipartFile file) {
        return attachmentService.upload(userId, taskId, file);
    }

    /**
     * Always sent as a download, never displayed inline, so an uploaded HTML or SVG
     * file cannot run in the browser under this site's origin.
     */
    @GetMapping("/{attachmentId}/download")
    @Operation(summary = "Download an attachment")
    public ResponseEntity<Resource> download(@CurrentUserId Long userId, @PathVariable Long taskId,
                                             @PathVariable Long attachmentId) {
        AttachmentService.Download file = attachmentService.download(userId, taskId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an attachment (its uploader or the task's creator)")
    public void delete(@CurrentUserId Long userId, @PathVariable Long taskId, @PathVariable Long attachmentId) {
        attachmentService.delete(userId, taskId, attachmentId);
    }
}
