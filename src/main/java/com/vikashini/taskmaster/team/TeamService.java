package com.vikashini.taskmaster.team;

import com.vikashini.taskmaster.common.ApiException;
import com.vikashini.taskmaster.notification.NotificationService;
import com.vikashini.taskmaster.notification.NotificationType;
import com.vikashini.taskmaster.task.TaskRepository;
import com.vikashini.taskmaster.team.dto.TeamDtos.CreateTeamRequest;
import com.vikashini.taskmaster.team.dto.TeamDtos.InvitationResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.MemberResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.TeamDetailResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.TeamResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.UpdateTeamRequest;
import com.vikashini.taskmaster.user.User;
import com.vikashini.taskmaster.user.UserService;
import com.vikashini.taskmaster.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Teams, their members and invitations.
 *
 * <p>Someone who is not a member gets 404 for a team rather than 403, so team ids
 * cannot be probed to find out which teams exist.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamInvitationRepository invitations;
    private final TaskRepository tasks;
    private final UserService userService;
    private final NotificationService notificationService;

    @Transactional
    public TeamResponse create(Long userId, CreateTeamRequest request) {
        User owner = userService.getById(userId);
        Team team = teams.save(new Team(request.name().trim(), blankToNull(request.description()), owner));
        TeamMember membership = members.save(new TeamMember(team, owner, TeamRole.OWNER));
        return TeamResponse.from(membership);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listMine(Long userId) {
        return members.findMembershipsOf(userId).stream().map(TeamResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TeamDetailResponse get(Long userId, Long teamId) {
        TeamMember membership = requireMembership(teamId, userId);
        return detail(membership);
    }

    @Transactional
    public TeamDetailResponse update(Long userId, Long teamId, UpdateTeamRequest request) {
        TeamMember membership = requireOwner(teamId, userId);
        Team team = membership.getTeam();
        if (request.name() != null) {
            team.setName(request.name().trim());
        }
        if (request.description() != null) {
            team.setDescription(blankToNull(request.description()));
        }
        return detail(membership);
    }

    @Transactional
    public InvitationResponse invite(Long userId, Long teamId, String email) {
        TeamMember ownerMembership = requireOwner(teamId, userId);
        Team team = ownerMembership.getTeam();
        User invitee = userService.getByEmail(email);

        if (members.existsByTeamIdAndUserId(teamId, invitee.getId())) {
            throw ApiException.conflict("This user is already a member of the team");
        }
        if (invitations.existsByTeamIdAndInvitedUserIdAndStatus(teamId, invitee.getId(),
                TeamInvitation.Status.PENDING)) {
            throw ApiException.conflict("This user already has a pending invitation to the team");
        }

        TeamInvitation invitation = invitations.save(
                new TeamInvitation(team, invitee, ownerMembership.getUser()));
        notificationService.notify(List.of(invitee.getId()), userId, NotificationType.TEAM_INVITATION,
                "%s invited you to join %s".formatted(ownerMembership.getUser().getName(), team.getName()),
                null, team.getId());
        return InvitationResponse.from(invitation);
    }

    /** Pending invitations of a team, for its owner. */
    @Transactional(readOnly = true)
    public List<InvitationResponse> pendingInvitationsOf(Long userId, Long teamId) {
        requireOwner(teamId, userId);
        return invitations.findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, TeamInvitation.Status.PENDING)
                .stream().map(InvitationResponse::from).toList();
    }

    /** Invitations waiting for this user's answer. */
    @Transactional(readOnly = true)
    public List<InvitationResponse> myPendingInvitations(Long userId) {
        return invitations.findByInvitedUserIdAndStatusOrderByCreatedAtDesc(userId, TeamInvitation.Status.PENDING)
                .stream().map(InvitationResponse::from).toList();
    }

    @Transactional
    public TeamDetailResponse accept(Long userId, Long invitationId) {
        TeamInvitation invitation = requireOpenInvitation(userId, invitationId);
        invitation.respond(TeamInvitation.Status.ACCEPTED);
        Team team = invitation.getTeam();
        TeamMember membership = members.findByTeamIdAndUserId(team.getId(), userId)
                .orElseGet(() -> members.save(new TeamMember(team, invitation.getInvitedUser(), TeamRole.MEMBER)));
        return detail(membership);
    }

    @Transactional
    public InvitationResponse decline(Long userId, Long invitationId) {
        TeamInvitation invitation = requireOpenInvitation(userId, invitationId);
        invitation.respond(TeamInvitation.Status.DECLINED);
        return InvitationResponse.from(invitation);
    }

    /**
     * The owner can remove anyone else; any member can remove themselves (leave). The
     * owner cannot leave, because a team always needs one. Tasks in this team that were
     * assigned to the departing member become unassigned.
     */
    @Transactional
    public void removeMember(Long userId, Long teamId, Long memberUserId) {
        TeamMember actor = requireMembership(teamId, userId);
        boolean leaving = userId.equals(memberUserId);
        if (!leaving && !actor.isOwner()) {
            throw ApiException.forbidden("Only the team owner can remove other members");
        }
        TeamMember target = members.findByTeamIdAndUserId(teamId, memberUserId)
                .orElseThrow(() -> ApiException.notFound("This user is not a member of the team"));
        if (target.isOwner()) {
            throw ApiException.conflict("The team owner cannot leave or be removed");
        }
        tasks.unassignInTeam(teamId, memberUserId, Instant.now());
        members.delete(target);
    }

    /** The caller's membership of the team, or 404 if they are not in it. */
    @Transactional(readOnly = true)
    public TeamMember requireMembership(Long teamId, Long userId) {
        return members.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long teamId, Long userId) {
        return members.existsByTeamIdAndUserId(teamId, userId);
    }

    private TeamMember requireOwner(Long teamId, Long userId) {
        TeamMember membership = requireMembership(teamId, userId);
        if (!membership.isOwner()) {
            throw ApiException.forbidden("Only the team owner can do this");
        }
        return membership;
    }

    private TeamInvitation requireOpenInvitation(Long userId, Long invitationId) {
        TeamInvitation invitation = invitations.findById(invitationId)
                .filter(i -> i.getInvitedUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));
        if (invitation.getStatus() != TeamInvitation.Status.PENDING) {
            throw ApiException.conflict("This invitation was already " + invitation.getStatus().name().toLowerCase());
        }
        return invitation;
    }

    private TeamDetailResponse detail(TeamMember membership) {
        Team team = membership.getTeam();
        List<MemberResponse> memberList = members.findByTeamIdOrderByJoinedAtAsc(team.getId())
                .stream().map(MemberResponse::from).toList();
        return new TeamDetailResponse(team.getId(), team.getName(), team.getDescription(),
                UserSummary.from(team.getOwner()), membership.getRole(), team.getCreatedAt(), memberList);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
