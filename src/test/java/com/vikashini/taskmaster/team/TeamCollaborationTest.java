package com.vikashini.taskmaster.team;

import com.vikashini.taskmaster.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamCollaborationTest extends IntegrationTest {

    @Test
    void theCreatorOfATeamIsItsOwner() throws Exception {
        TestUser asha = signUp("Asha");

        postAs(asha, "/api/teams", Map.of("name", "Platform", "description", "Backend work"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.owner.id").value(asha.id()));
        getAs(asha, "/api/teams").andExpect(jsonPath("$[*].name", contains("Platform")));
    }

    @Test
    void anInviteeJoinsByAcceptingAndIsNotifiedOfTheInvitation() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");

        postAs(asha, "/api/teams/" + teamId + "/invitations", Map.of("email", "RAVI@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        getAs(ravi, "/api/notifications")
                .andExpect(jsonPath("$.content[0].type").value("TEAM_INVITATION"))
                .andExpect(jsonPath("$.content[0].teamId").value(teamId));
        long invitationId = body(getAs(ravi, "/api/invitations")
                .andExpect(jsonPath("$", hasSize(1)))).get(0).get("id").asLong();

        postAs(ravi, "/api/invitations/" + invitationId + "/accept", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members[*].user.name", contains("Asha", "Ravi")));

        getAs(ravi, "/api/invitations").andExpect(jsonPath("$", hasSize(0)));
        postAs(ravi, "/api/invitations/" + invitationId + "/accept", Map.of()).andExpect(status().isConflict());
    }

    @Test
    void aDeclinedInvitationDoesNotAddTheMember() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        long invitationId = body(postAs(asha, "/api/teams/" + teamId + "/invitations",
                Map.of("email", ravi.email()))).get("id").asLong();

        postAs(ravi, "/api/invitations/" + invitationId + "/decline", Map.of())
                .andExpect(jsonPath("$.status").value("DECLINED"));
        getAs(ravi, "/api/teams/" + teamId).andExpect(status().isNotFound());

        // Declining does not block a fresh invitation later.
        postAs(asha, "/api/teams/" + teamId + "/invitations", Map.of("email", ravi.email()))
                .andExpect(status().isCreated());
    }

    @Test
    void invitationsAreCheckedBeforeTheyAreSent() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        TestUser meera = signUp("Meera");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        String invitations = "/api/teams/" + teamId + "/invitations";

        postAs(asha, invitations, Map.of("email", "ghost@example.com")).andExpect(status().isNotFound());
        postAs(asha, invitations, Map.of("email", ravi.email())).andExpect(status().isConflict());
        postAs(asha, invitations, Map.of("email", meera.email())).andExpect(status().isCreated());
        postAs(asha, invitations, Map.of("email", meera.email())).andExpect(status().isConflict());
        // Only the owner may invite.
        postAs(ravi, invitations, Map.of("email", "x@example.com")).andExpect(status().isForbidden());
        // Someone else's invitation cannot be accepted.
        long invitationId = body(getAs(asha, invitations)).get(0).get("id").asLong();
        postAs(ravi, "/api/invitations/" + invitationId + "/accept", Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void outsidersCannotSeeATeamOrItsTasks() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser outsider = signUp("Outsider");
        long teamId = createTeam(asha, "Platform");
        long taskId = createTask(asha, Map.of("title", "Team secret", "teamId", teamId));

        getAs(outsider, "/api/teams/" + teamId).andExpect(status().isNotFound());
        getAs(outsider, "/api/tasks/" + taskId).andExpect(status().isNotFound());
        getAs(outsider, "/api/tasks?teamId=" + teamId).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void everyMemberSeesTheTeamsTasksIncludingUnassignedOnes() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        createTask(asha, Map.of("title", "Unassigned", "teamId", teamId));
        createTask(asha, Map.of("title", "Personal of Asha"));

        getAs(ravi, "/api/tasks").andExpect(jsonPath("$.content[*].title", contains("Unassigned")));
        getAs(ravi, "/api/tasks?unassigned=true&teamId=" + teamId)
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void assigningATaskToATeamMemberNotifiesThem() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        long taskId = createTask(asha, Map.of("title", "Review PR", "teamId", teamId));

        putAs(asha, "/api/tasks/" + taskId + "/assignee", Map.of("assigneeId", ravi.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(ravi.id()));

        getAs(ravi, "/api/tasks?assignedToMe=true").andExpect(jsonPath("$.content[*].title", contains("Review PR")));
        getAs(ravi, "/api/notifications?unreadOnly=true")
                .andExpect(jsonPath("$.content[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].taskId").value(taskId))
                .andExpect(jsonPath("$.content[0].message").value("Asha assigned \"Review PR\" to you"));
    }

    @Test
    void theAssigneeCompletingATaskNotifiesTheCreatorButNotThemselves() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        long taskId = createTask(asha, Map.of("title", "Deploy", "teamId", teamId, "assigneeId", ravi.id()));

        patchAs(ravi, "/api/tasks/" + taskId + "/status", Map.of("status", "COMPLETED")).andExpect(status().isOk());

        getAs(asha, "/api/notifications")
                .andExpect(jsonPath("$.content[0].type").value("TASK_UPDATED"))
                .andExpect(jsonPath("$.content[0].message").value("Ravi marked \"Deploy\" as completed"));
        getAs(ravi, "/api/notifications").andExpect(jsonPath("$.content[*].type", contains("TASK_ASSIGNED", "TEAM_INVITATION")));
    }

    @Test
    void tasksCanOnlyBeAssignedToMembersOfTheirTeam() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser outsider = signUp("Outsider");
        long teamId = createTeam(asha, "Platform");
        long taskId = createTask(asha, Map.of("title", "Review PR", "teamId", teamId));

        putAs(asha, "/api/tasks/" + taskId + "/assignee", Map.of("assigneeId", outsider.id()))
                .andExpect(status().isBadRequest());

        Map<String, Object> unassign = new HashMap<>();
        unassign.put("assigneeId", null);
        putAs(asha, "/api/tasks/" + taskId + "/assignee", unassign)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value(nullValue()));
    }

    @Test
    void onlyTheCreatorOrTheTeamOwnerCanDeleteATeamTask() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        TestUser meera = signUp("Meera");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        addToTeam(asha, teamId, meera);
        long byRavi = createTask(ravi, Map.of("title", "Ravi's", "teamId", teamId));

        deleteAs(meera, "/api/tasks/" + byRavi).andExpect(status().isForbidden());
        deleteAs(asha, "/api/tasks/" + byRavi).andExpect(status().isNoContent());
    }

    @Test
    void leavingATeamUnassignsTheLeaversTasksThere() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        long taskId = createTask(asha, Map.of("title", "Review PR", "teamId", teamId, "assigneeId", ravi.id()));

        deleteAs(ravi, "/api/teams/" + teamId + "/members/" + ravi.id()).andExpect(status().isNoContent());

        getAs(ravi, "/api/teams/" + teamId).andExpect(status().isNotFound());
        getAs(asha, "/api/tasks/" + taskId).andExpect(jsonPath("$.assignee").value(nullValue()));
    }

    @Test
    void membershipRules() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        TestUser meera = signUp("Meera");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        addToTeam(asha, teamId, meera);
        String members = "/api/teams/" + teamId + "/members/";

        deleteAs(ravi, members + meera.id()).andExpect(status().isForbidden());
        deleteAs(asha, members + asha.id()).andExpect(status().isConflict());
        deleteAs(asha, members + meera.id()).andExpect(status().isNoContent());
        getAs(asha, "/api/teams/" + teamId)
                .andExpect(jsonPath("$.members[*].user.name", containsInAnyOrder("Asha", "Ravi")));
    }

    @Test
    void onlyTheOwnerCanRenameATeam() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);

        patchAs(ravi, "/api/teams/" + teamId, Map.of("name", "Mine now")).andExpect(status().isForbidden());
        patchAs(asha, "/api/teams/" + teamId, Map.of("name", "Platform Team"))
                .andExpect(jsonPath("$.name").value("Platform Team"));
    }
}
