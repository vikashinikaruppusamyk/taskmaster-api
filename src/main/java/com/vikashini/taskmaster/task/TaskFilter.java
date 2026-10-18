package com.vikashini.taskmaster.task;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Query parameters for {@code GET /api/tasks}. Every parameter is optional and they
 * combine with AND; the result is always limited to tasks the caller can see.
 */
public record TaskFilter(
        @Parameter(description = "One or more statuses, e.g. status=OPEN&status=IN_PROGRESS")
        List<TaskStatus> status,

        @Parameter(description = "Only tasks with this priority")
        TaskPriority priority,

        @Parameter(description = "Only tasks of this team")
        Long teamId,

        @Parameter(description = "Only tasks assigned to this user")
        Long assigneeId,

        @Parameter(description = "true: only tasks assigned to me")
        Boolean assignedToMe,

        @Parameter(description = "true: only tasks nobody is assigned to")
        Boolean unassigned,

        @Parameter(description = "Case-insensitive text to look for in the title or description")
        String q,

        @Parameter(description = "Due on or after this date (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dueFrom,

        @Parameter(description = "Due on or before this date (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dueTo) {
}
