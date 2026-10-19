package com.vikashini.taskmaster.task;

import com.vikashini.taskmaster.attachment.AttachmentRepository;
import com.vikashini.taskmaster.attachment.AttachmentStorage;
import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.common.PageResponse;
import com.vikashini.taskmaster.notification.NotificationService;
import com.vikashini.taskmaster.notification.NotificationType;
import com.vikashini.taskmaster.task.dto.TaskDtos.CreateTaskRequest;
import com.vikashini.taskmaster.task.dto.TaskDtos.TaskResponse;
import com.vikashini.taskmaster.task.dto.TaskDtos.UpdateTaskRequest;
import com.vikashini.taskmaster.team.Team;
import com.vikashini.taskmaster.team.TeamService;
import com.vikashini.taskmaster.user.User;
import com.vikashini.taskmaster.user.UserRepository;
import com.vikashini.taskmaster.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskService {

    /** Fields a client may sort by. */
    private static final Set<String> SORTABLE =
            Set.of("dueDate", "priority", "status", "title", "createdAt", "updatedAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TaskRepository tasks;
    private final TaskAccess access;
    private final TeamService teamService;
    private final UserService userService;
    private final UserRepository users;
    private final NotificationService notificationService;
    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final Clock clock;

    @Transactional
    public TaskResponse create(Long userId, CreateTaskRequest request) {
        User creator = userService.getById(userId);
        Team team = request.teamId() == null ? null
                : teamService.requireMembership(request.teamId(), userId).getTeam();
        access.checkAssignable(team, userId, request.assigneeId());

        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setTeam(team);
        task.setCreatedBy(creator);
        task.setAssignee(request.assigneeId() == null ? null : users.getReferenceById(request.assigneeId()));
        tasks.save(task);

        if (task.getAssignee() != null) {
            notifyAssigned(task, creator);
        }
        return toResponse(reload(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long userId, Long taskId) {
        return toResponse(access.requireVisible(taskId, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long userId, TaskFilter filter, Pageable pageable) {
        var spec = TaskSpecifications.visibleTo(userId).and(TaskSpecifications.matching(filter, userId));
        LocalDate today = LocalDate.now(clock);
        return PageResponse.of(tasks.findAll(spec, withAllowedSort(pageable)),
                task -> TaskResponse.from(task, today));
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, UpdateTaskRequest request) {
        Task task = access.requireVisible(taskId, userId);
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority());
        tasks.flush();

        notifyWatchers(task, userId, "%s updated \"%s\"".formatted(actorName(userId), task.getTitle()));
        return toResponse(task);
    }

    /** Marks a task open, in progress or completed. */
    @Transactional
    public TaskResponse changeStatus(Long userId, Long taskId, TaskStatus status) {
        Task task = access.requireVisible(taskId, userId);
        if (task.getStatus() != status) {
            task.changeStatus(status);
            tasks.flush();
            notifyWatchers(task, userId, "%s marked \"%s\" as %s".formatted(
                    actorName(userId), task.getTitle(), status.name().toLowerCase().replace('_', ' ')));
        }
        return toResponse(task);
    }

    /** Assigns the task to someone, or to nobody when {@code assigneeId} is null. */
    @Transactional
    public TaskResponse assign(Long userId, Long taskId, Long assigneeId) {
        Task task = access.requireVisible(taskId, userId);
        Long currentId = task.getAssignee() == null ? null : task.getAssignee().getId();
        if (Objects.equals(currentId, assigneeId)) {
            return toResponse(task);
        }
        access.checkAssignable(task.getTeam(), task.getCreatedBy().getId(), assigneeId);
        task.setAssignee(assigneeId == null ? null : userService.getById(assigneeId));
        tasks.flush();

        if (task.getAssignee() != null) {
            notifyAssigned(task, userService.getById(userId));
        }
        return toResponse(task);
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        Task task = access.requireVisible(taskId, userId);
        access.checkCanDelete(task, userId);
        List<String> files = attachments.findStorageKeysByTaskId(taskId);
        tasks.delete(task);
        // Comments and attachment rows go with the task (ON DELETE CASCADE); the files
        // on disk are removed once the deletion has committed.
        storage.deleteAfterCommit(files);
    }

    private void notifyAssigned(Task task, User actor) {
        notificationService.notify(List.of(task.getAssignee().getId()), actor.getId(),
                NotificationType.TASK_ASSIGNED,
                "%s assigned \"%s\" to you".formatted(actor.getName(), task.getTitle()),
                task.getId(), task.teamId());
    }

    /** Tells the creator and the assignee, except whoever made the change. */
    private void notifyWatchers(Task task, Long actorId, String message) {
        notificationService.notify(task.watcherIds(), actorId, NotificationType.TASK_UPDATED, message,
                task.getId(), task.teamId());
    }

    private String actorName(Long userId) {
        return userService.getById(userId).getName();
    }

    private Task reload(Task task) {
        tasks.flush();
        return tasks.findWithPeopleById(task.getId()).orElseThrow();
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.from(task, LocalDate.now(clock));
    }

    /**
     * Rejects unknown sort fields with 400 instead of letting them reach the database,
     * and adds the id as a tie-breaker so that pages never overlap.
     */
    private static Pageable withAllowedSort(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw ApiException.badRequest("Cannot sort by '%s'; use one of %s"
                        .formatted(order.getProperty(), SORTABLE.stream().sorted().toList()));
            }
            orders.add(order);
        }
        Sort sort = orders.isEmpty() ? DEFAULT_SORT : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                sort.and(Sort.by(Sort.Order.desc("id"))));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
