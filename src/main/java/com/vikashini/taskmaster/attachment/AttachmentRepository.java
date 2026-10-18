package com.vikashini.taskmaster.attachment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<Attachment> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);

    @EntityGraph(attributePaths = "uploadedBy")
    Optional<Attachment> findByIdAndTaskId(Long id, Long taskId);

    @Query("SELECT a.storageKey FROM Attachment a WHERE a.task.id = :taskId")
    List<String> findStorageKeysByTaskId(Long taskId);
}
