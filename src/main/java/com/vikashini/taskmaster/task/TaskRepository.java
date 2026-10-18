package com.vikashini.taskmaster.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** Loads the people and team shown in every task response in the same query. */
    @Override
    @EntityGraph(attributePaths = {"createdBy", "assignee", "team"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "assignee", "team"})
    Optional<Task> findWithPeopleById(Long id);

    /** Used when someone leaves a team: their tasks in it go back to nobody. */
    @Modifying
    @Query("UPDATE Task t SET t.assignee = null, t.updatedAt = :now "
            + "WHERE t.team.id = :teamId AND t.assignee.id = :userId")
    int unassignInTeam(Long teamId, Long userId, Instant now);
}
