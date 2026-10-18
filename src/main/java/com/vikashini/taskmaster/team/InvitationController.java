package com.vikashini.taskmaster.team;

import com.vikashini.taskmaster.auth.CurrentUserId;
import com.vikashini.taskmaster.team.dto.TeamDtos.InvitationResponse;
import com.vikashini.taskmaster.team.dto.TeamDtos.TeamDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Invitations from the invitee's side: see them, join the team, or say no. */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations")
public class InvitationController {

    private final TeamService teamService;

    @GetMapping
    @Operation(summary = "List invitations waiting for my answer")
    public List<InvitationResponse> mine(@CurrentUserId Long userId) {
        return teamService.myPendingInvitations(userId);
    }

    @PostMapping("/{invitationId}/accept")
    @Operation(summary = "Accept an invitation and join the team")
    public TeamDetailResponse accept(@CurrentUserId Long userId, @PathVariable Long invitationId) {
        return teamService.accept(userId, invitationId);
    }

    @PostMapping("/{invitationId}/decline")
    @Operation(summary = "Decline an invitation")
    public InvitationResponse decline(@CurrentUserId Long userId, @PathVariable Long invitationId) {
        return teamService.decline(userId, invitationId);
    }
}
