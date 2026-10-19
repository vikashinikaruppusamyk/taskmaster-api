package com.vikashini.taskmaster.user.dto;

import com.vikashini.taskmaster.user.User;

/** How a user appears inside other resources: a task's assignee, a comment's author, ... */
public record UserSummary(Long id, String name, String email) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getName(), user.getEmail());
    }
}
