package com.vikashini.taskmaster.task;

import com.vikashini.taskmaster.team.TeamMember;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the WHERE clause for task listing, filtering and search. */
final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /**
     * Tasks the user may see: ones they created, ones assigned to them, and every task
     * of a team they belong to.
     */
    static Specification<Task> visibleTo(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> myTeams = query.subquery(Long.class);
            var member = myTeams.from(TeamMember.class);
            myTeams.select(member.get("team").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));

            return cb.or(
                    cb.equal(root.get("createdBy").get("id"), userId),
                    cb.equal(root.get("assignee").get("id"), userId),
                    root.get("team").get("id").in(myTeams));
        };
    }

    static Specification<Task> matching(TaskFilter filter, Long userId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.teamId() != null) {
                predicates.add(cb.equal(root.get("team").get("id"), filter.teamId()));
            }
            if (filter.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
            }
            if (Boolean.TRUE.equals(filter.assignedToMe())) {
                predicates.add(cb.equal(root.get("assignee").get("id"), userId));
            }
            if (Boolean.TRUE.equals(filter.unassigned())) {
                predicates.add(cb.isNull(root.get("assignee")));
            }
            if (filter.dueFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filter.dueFrom()));
            }
            if (filter.dueTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.dueTo()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = containsPattern(filter.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * A LIKE pattern that matches the text anywhere. {@code %} and {@code _} in the
     * search text are escaped, so searching for "100%" finds exactly that.
     */
    static String containsPattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
