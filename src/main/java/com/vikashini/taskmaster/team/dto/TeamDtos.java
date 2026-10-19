package com.vikashini.taskmaster.team.dto;

import com.vikashini.taskmaster.team.Team;
import com.vikashini.taskmaster.team.TeamInvitation;
import com.vikashini.taskmaster.team.TeamMember;
import com.vikashini.taskmaster.team.TeamRole;
import com.vikashini.taskmaster.user.dto.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Requests and responses for teams, members and invitations. */
public final class TeamDtos {

    private TeamDtos() {
    }

    public record CreateTeamRequest(
            @NotBlank(message = "is required")
            @Size(max = 100, message = "must be at most 100 characters")
            String name,

            @Size(max = 1000, message = "must be at most 1000 characters")
            String description) {
    }

    /** Partial update: leave a field out to keep it. An empty description clears it. */
    public record UpdateTeamRequest(
            @Size(max = 100, message = "must be at most 100 characters")
            @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
            String name,

            @Size(max = 1000, message = "must be at most 1000 characters")
            String description) {
    }

    public record InviteRequest(
            @NotBlank(message = "is required")
            @Email(message = "must be a valid email address")
            String email) {

        public InviteRequest {
            email = email == null ? null : email.trim();
        }
    }

    /** A team as seen by one of its members; {@code myRole} is that member's role. */
    public record TeamResponse(
            Long id,
            String name,
            String description,
            UserSummary owner,
            TeamRole myRole,
            Instant createdAt) {

        public static TeamResponse from(TeamMember membership) {
            Team team = membership.getTeam();
            return new TeamResponse(team.getId(), team.getName(), team.getDescription(),
                    UserSummary.from(team.getOwner()), membership.getRole(), team.getCreatedAt());
        }
    }

    public record TeamDetailResponse(
            Long id,
            String name,
            String description,
            UserSummary owner,
            TeamRole myRole,
            Instant createdAt,
            List<MemberResponse> members) {
    }

    public record MemberResponse(UserSummary user, TeamRole role, Instant joinedAt) {

        public static MemberResponse from(TeamMember member) {
            return new MemberResponse(UserSummary.from(member.getUser()), member.getRole(), member.getJoinedAt());
        }
    }

    public record TeamRef(Long id, String name) {
    }

    public record InvitationResponse(
            Long id,
            TeamRef team,
            UserSummary invitedUser,
            UserSummary invitedBy,
            TeamInvitation.Status status,
            Instant createdAt,
            Instant respondedAt) {

        public static InvitationResponse from(TeamInvitation invitation) {
            Team team = invitation.getTeam();
            return new InvitationResponse(invitation.getId(), new TeamRef(team.getId(), team.getName()),
                    UserSummary.from(invitation.getInvitedUser()), UserSummary.from(invitation.getInvitedBy()),
                    invitation.getStatus(), invitation.getCreatedAt(), invitation.getRespondedAt());
        }
    }
}
