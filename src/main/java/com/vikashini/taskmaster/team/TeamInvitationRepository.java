package com.vikashini.taskmaster.team;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    @EntityGraph(attributePaths = {"team", "invitedBy", "invitedUser"})
    List<TeamInvitation> findByInvitedUserIdAndStatusOrderByCreatedAtDesc(Long userId, TeamInvitation.Status status);

    @EntityGraph(attributePaths = {"team", "invitedBy", "invitedUser"})
    List<TeamInvitation> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, TeamInvitation.Status status);

    boolean existsByTeamIdAndInvitedUserIdAndStatus(Long teamId, Long userId, TeamInvitation.Status status);
}
