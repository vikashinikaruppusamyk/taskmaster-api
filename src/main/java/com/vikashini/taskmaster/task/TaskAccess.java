package com.vikashini.taskmaster.task;

import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.team.Team;
import com.vikashini.taskmaster.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may see, change and delete a task. Tasks, comments and attachments all ask here,
 * so the rules live in one place.
 *
 * <ul>
 *   <li>See and edit: the creator, the assignee, and every member of the task's team.</li>
 *   <li>Delete: the creator, or the owner of the task's team.</li>
 *   <li>Assign: to a member of the task's team; a personal task only to its creator.</li>
 * </ul>
 * A task the caller cannot see is reported as 404, so its existence is not revealed.
 */
@Component
@RequiredArgsConstructor
public class TaskAccess {

    private final TaskRepository tasks;
    private final TeamService teamService;

    @Transactional(readOnly = true)
    public Task requireVisible(Long taskId, Long userId) {
        return tasks.findWithPeopleById(taskId)
                .filter(task -> canSee(task, userId))
                .orElseThrow(() -> ApiException.notFound("Task not found"));
    }

    boolean canSee(Task task, Long userId) {
        return userId.equals(task.getCreatedBy().getId())
                || (task.getAssignee() != null && userId.equals(task.getAssignee().getId()))
                || (task.getTeam() != null && teamService.isMember(task.getTeam().getId(), userId));
    }

    void checkCanDelete(Task task, Long userId) {
        boolean creator = userId.equals(task.getCreatedBy().getId());
        boolean teamOwner = task.getTeam() != null && userId.equals(task.getTeam().getOwner().getId());
        if (!creator && !teamOwner) {
            throw ApiException.forbidden("Only the task's creator or the team owner can delete it");
        }
    }

    /** Checks that {@code assigneeId} may work on a task of {@code team} created by {@code creatorId}. */
    void checkAssignable(Team team, Long creatorId, Long assigneeId) {
        if (assigneeId == null) {
            return;
        }
        if (team == null) {
            if (!assigneeId.equals(creatorId)) {
                throw ApiException.badRequest(
                        "A personal task can only be assigned to its creator; put it in a team to share it");
            }
        } else if (!teamService.isMember(team.getId(), assigneeId)) {
            throw ApiException.badRequest("The assignee must be a member of the task's team");
        }
    }
}
