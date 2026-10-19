package com.vikashini.taskmaster.team;

import com.vikashini.taskmaster.auth.CurrentUserId;
import com.vikashini.taskmaster.team.dto.TeamDtos.CreateTeamRequest;
import com.vikashini.taskmaster.team.dto.TeamDtos.InvitationResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.InviteRequest;
import com.vikashini.taskmaster.team.dto.TeamDtos.TeamDetailResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.TeamResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.UpdateTeamRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "Teams")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a team; I become its owner")
    public TeamResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateTeamRequest request) {
        return teamService.create(userId, request);
    }

    @GetMapping
    @Operation(summary = "List the teams I belong to")
    public List<TeamResponse> listMine(@CurrentUserId Long userId) {
        return teamService.listMine(userId);
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "View a team and its members")
    public TeamDetailResponse get(@CurrentUserId Long userId, @PathVariable Long teamId) {
        return teamService.get(userId, teamId);
    }

    @PatchMapping("/{teamId}")
    @Operation(summary = "Rename a team or change its description (owner only)")
    public TeamDetailResponse update(@CurrentUserId Long userId, @PathVariable Long teamId,
                                     @Valid @RequestBody UpdateTeamRequest request) {
        return teamService.update(userId, teamId, request);
    }

    @PostMapping("/{teamId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite a registered user by email (owner only)")
    public InvitationResponse invite(@CurrentUserId Long userId, @PathVariable Long teamId,
                                     @Valid @RequestBody InviteRequest request) {
        return teamService.invite(userId, teamId, request.email());
    }

    @GetMapping("/{teamId}/invitations")
    @Operation(summary = "List the team's pending invitations (owner only)")
    public List<InvitationResponse> pendingInvitations(@CurrentUserId Long userId, @PathVariable Long teamId) {
        return teamService.pendingInvitationsOf(userId, teamId);
    }

    @DeleteMapping("/{teamId}/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member (owner), or leave the team (my own id)")
    public void removeMember(@CurrentUserId Long userId, @PathVariable Long teamId,
                             @PathVariable Long memberUserId) {
        teamService.removeMember(userId, teamId, memberUserId);
    }
}
