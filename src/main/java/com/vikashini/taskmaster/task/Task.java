package com.vikashini.taskmaster.task;

import com.vikashini.taskmaster.team.Team;
import com.vikashini.taskmaster.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A task is either personal ({@code team} is null, only its creator works on it) or
 * belongs to a team, in which case every member can see and edit it.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.OPEN;

    @Column(nullable = false)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** When the task last became COMPLETED; cleared if it is reopened. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Moves the task to a new status, keeping {@code completedAt} in step. */
    public void changeStatus(TaskStatus newStatus) {
        if (newStatus == status) {
            return;
        }
        status = newStatus;
        completedAt = newStatus == TaskStatus.COMPLETED ? Instant.now() : null;
    }

    public boolean isPersonal() {
        return team == null;
    }

    /** The people who hear about changes and comments: the creator and the assignee. */
    public List<Long> watcherIds() {
        List<Long> ids = new ArrayList<>();
        ids.add(createdBy.getId());
        if (assignee != null) {
            ids.add(assignee.getId());
        }
        return ids;
    }

    public Long teamId() {
        return team == null ? null : team.getId();
    }

    public boolean isOverdue(LocalDate today) {
        return dueDate != null && status != TaskStatus.COMPLETED && dueDate.isBefore(today);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
