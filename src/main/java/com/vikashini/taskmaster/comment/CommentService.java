package com.vikashini.taskmaster.comment;

import com.vikashini.taskmaster.comment.CommentDtos.CommentResponse;
import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.notification.NotificationService;
import com.vikashini.taskmaster.notification.NotificationType;
import com.vikashini.taskmaster.task.Task;
import com.vikashini.taskmaster.task.TaskAccess;
import com.vikashini.taskmaster.user.User;
import com.vikashini.taskmaster.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Comments on a task. Anyone who can see the task can read and add comments; only the
 * author can edit one, and the author or the task's creator can delete it.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository comments;
    private final TaskAccess taskAccess;
    private final UserService userService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long userId, Long taskId) {
        taskAccess.requireVisible(taskId, userId);
        return comments.findByTaskIdOrderByCreatedAtAscIdAsc(taskId).stream().map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse add(Long userId, Long taskId, String body) {
        Task task = taskAccess.requireVisible(taskId, userId);
        User author = userService.getById(userId);
        Comment comment = comments.save(new Comment(task, author, body.trim()));

        notificationService.notify(task.watcherIds(), userId, NotificationType.TASK_COMMENTED,
                "%s commented on \"%s\"".formatted(author.getName(), task.getTitle()),
                task.getId(), task.teamId());
        return CommentResponse.from(comment);
    }

    @Transactional
    public CommentResponse edit(Long userId, Long taskId, Long commentId, String body) {
        Comment comment = requireComment(userId, taskId, commentId);
        if (!comment.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("Only the author can edit a comment");
        }
        comment.setBody(body.trim());
        comments.flush();
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long userId, Long taskId, Long commentId) {
        Comment comment = requireComment(userId, taskId, commentId);
        boolean author = comment.getAuthor().getId().equals(userId);
        boolean taskCreator = comment.getTask().getCreatedBy().getId().equals(userId);
        if (!author && !taskCreator) {
            throw ApiException.forbidden("Only the author or the task's creator can delete a comment");
        }
        comments.delete(comment);
    }

    private Comment requireComment(Long userId, Long taskId, Long commentId) {
        taskAccess.requireVisible(taskId, userId);
        return comments.findByIdAndTaskId(commentId, taskId)
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
    }
}
