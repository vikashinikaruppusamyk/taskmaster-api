package com.vikashini.taskmaster.notification;

public enum NotificationType {
    /** A task was assigned to you. */
    TASK_ASSIGNED,
    /** A task you created or are assigned to was changed by someone else. */
    TASK_UPDATED,
    /** Someone commented on a task you created or are assigned to. */
    TASK_COMMENTED,
    /** You were invited to join a team. */
    TEAM_INVITATION
}
