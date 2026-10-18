package com.vikashini.taskmaster.task.dto;

import com.vikashini.taskmaster.task.Task;
import com.vikashini.taskmaster.task.TaskPriority;
import com.vikashini.taskmaster.task.TaskStatus;
import com.vikashini.taskmaster.team.dto.TeamDtos.TeamRef;
import com.vikashini.taskmaster.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** Requests and responses for tasks. */
public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotBlank(message = "is required")
            @Size(max = 200, message = "must be at most 200 characters")
            String title,

            @Size(max = 5000, message = "must be at most 5000 characters")
            String description,

            @Schema(description = "yyyy-MM-dd; optional")
            LocalDate dueDate,

            @Schema(description = "Defaults to MEDIUM")
            TaskPriority priority,

            @Schema(description = "Leave out for a personal task")
            Long teamId,

            @Schema(description = "Must be a member of the team; for a personal task only yourself")
            Long assigneeId) {
    }

    /** Replaces the editable fields. A missing description or due date clears it. */
    public record UpdateTaskRequest(
            @NotBlank(message = "is required")
            @Size(max = 200, message = "must be at most 200 characters")
            String title,

            @Size(max = 5000, message = "must be at most 5000 characters")
            String description,

            LocalDate dueDate,

            @NotNull(message = "is required")
            TaskPriority priority) {
    }

    public record ChangeStatusRequest(
            @NotNull(message = "is required")
            TaskStatus status) {
    }

    public record AssignRequest(
            @Schema(description = "User to assign; null to unassign")
            Long assigneeId) {
    }

    public record TaskResponse(
            Long id,
            String title,
            String description,
            LocalDate dueDate,
            TaskStatus status,
            TaskPriority priority,
            @Schema(description = "Null for a personal task")
            TeamRef team,
            UserSummary createdBy,
            UserSummary assignee,
            @Schema(description = "Past its due date and not completed")
            boolean overdue,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {

        public static TaskResponse from(Task task, LocalDate today) {
            TeamRef team = task.getTeam() == null ? null
                    : new TeamRef(task.getTeam().getId(), task.getTeam().getName());
            return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getDueDate(),
                    task.getStatus(), task.getPriority(), team, UserSummary.from(task.getCreatedBy()),
                    UserSummary.from(task.getAssignee()), task.isOverdue(today), task.getCompletedAt(),
                    task.getCreatedAt(), task.getUpdatedAt());
        }
    }
}
