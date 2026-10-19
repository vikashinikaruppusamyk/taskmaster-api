package com.vikashini.taskmaster.comment;

import com.vikashini.taskmaster.auth.CurrentUserId;
import com.vikashini.taskmaster.comment.CommentDtos.CommentRequest;
import com.vikashini.taskmaster.comment.CommentDtos.CommentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "List a task's comments, oldest first")
    public List<CommentResponse> list(@CurrentUserId Long userId, @PathVariable Long taskId) {
        return commentService.list(userId, taskId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment on a task")
    public CommentResponse add(@CurrentUserId Long userId, @PathVariable Long taskId,
                               @Valid @RequestBody CommentRequest request) {
        return commentService.add(userId, taskId, request.body());
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Edit my comment")
    public CommentResponse edit(@CurrentUserId Long userId, @PathVariable Long taskId,
                                @PathVariable Long commentId, @Valid @RequestBody CommentRequest request) {
        return commentService.edit(userId, taskId, commentId, request.body());
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a comment (its author or the task's creator)")
    public void delete(@CurrentUserId Long userId, @PathVariable Long taskId, @PathVariable Long commentId) {
        commentService.delete(userId, taskId, commentId);
    }
}
