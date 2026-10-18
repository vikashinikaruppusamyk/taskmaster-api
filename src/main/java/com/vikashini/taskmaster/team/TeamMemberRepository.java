package com.vikashini.taskmaster.team;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @EntityGraph(attributePaths = "team")
    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);

    /** The teams a user belongs to, each with that user's own membership. */
    @EntityGraph(attributePaths = {"team", "team.owner"})
    @Query("SELECT m FROM TeamMember m WHERE m.user.id = :userId ORDER BY m.team.name")
    List<TeamMember> findMembershipsOf(Long userId);

    long countByTeamId(Long teamId);
}
