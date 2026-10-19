package com.vikashini.taskmaster.task;

import com.vikashini.taskmaster.auth.CurrentUserId;
import com.vikashini.taskmaster.common.PageResponse;
import com.vikashini.taskmaster.task.dto.TaskDtos.AssignRequest;
import com.vikashini.taskmaster.task.dto.TaskDtos.ChangeStatusRequest;
import com.vikashini.taskmaster.task.dto.TaskDtos.CreateTaskRequest;
import com.vikashini.taskmaster.task.dto.TaskDtos.TaskResponse;
import com.vikashini.taskmaster.task.dto.TaskDtos.UpdateTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task, personal or in one of my teams")
    public TaskResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(userId, request);
    }

    @GetMapping
    @Operation(summary = "List, filter, search and sort the tasks I can see",
            description = "Sort with sort=field,direction, e.g. sort=dueDate,asc. Sortable fields: "
                    + "dueDate, priority, status, title, createdAt, updatedAt. Default: newest first.")
    public PageResponse<TaskResponse> list(@CurrentUserId Long userId,
                                           @ParameterObject TaskFilter filter,
                                           @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return taskService.list(userId, filter, pageable);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "View a task")
    public TaskResponse get(@CurrentUserId Long userId, @PathVariable Long taskId) {
        return taskService.get(userId, taskId);
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Edit a task's title, description, due date and priority")
    public TaskResponse update(@CurrentUserId Long userId, @PathVariable Long taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(userId, taskId, request);
    }

    @PatchMapping("/{taskId}/status")
    @Operation(summary = "Change a task's status, e.g. mark it COMPLETED")
    public TaskResponse changeStatus(@CurrentUserId Long userId, @PathVariable Long taskId,
                                     @Valid @RequestBody ChangeStatusRequest request) {
        return taskService.changeStatus(userId, taskId, request.status());
    }

    @PutMapping("/{taskId}/assignee")
    @Operation(summary = "Assign a task to a team member, or unassign it with null")
    public TaskResponse assign(@CurrentUserId Long userId, @PathVariable Long taskId,
                               @RequestBody AssignRequest request) {
        return taskService.assign(userId, taskId, request.assigneeId());
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task (its creator or the team owner)")
    public void delete(@CurrentUserId Long userId, @PathVariable Long taskId) {
        taskService.delete(userId, taskId);
    }
}
