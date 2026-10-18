package com.vikashini.taskmaster.comment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    List<Comment> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);

    @EntityGraph(attributePaths = "author")
    Optional<Comment> findByIdAndTaskId(Long id, Long taskId);
}
