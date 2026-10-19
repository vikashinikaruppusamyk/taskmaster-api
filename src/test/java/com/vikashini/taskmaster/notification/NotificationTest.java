package com.vikashini.taskmaster.notification;

import com.vikashini.taskmaster.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationTest extends IntegrationTest {

    @Test
    void notificationsCanBeCountedAndMarkedRead() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        createTask(asha, Map.of("title", "One", "teamId", teamId, "assigneeId", ravi.id()));
        createTask(asha, Map.of("title", "Two", "teamId", teamId, "assigneeId", ravi.id()));

        // The invitation plus two assignments.
        getAs(ravi, "/api/notifications/unread-count").andExpect(jsonPath("$.unread").value(3));

        long newest = body(getAs(ravi, "/api/notifications")).get("content").get(0).get("id").asLong();
        patchAs(ravi, "/api/notifications/" + newest + "/read", Map.of())
                .andExpect(jsonPath("$.read").value(true));
        getAs(ravi, "/api/notifications?unreadOnly=true").andExpect(jsonPath("$.totalElements").value(2));

        postAs(ravi, "/api/notifications/read-all", Map.of()).andExpect(jsonPath("$.updated").value(2));
        getAs(ravi, "/api/notifications/unread-count").andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void nobodyCanReadSomeoneElsesNotification() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        postAs(asha, "/api/teams/" + teamId + "/invitations", Map.of("email", ravi.email()));
        long ravisNotification = body(getAs(ravi, "/api/notifications")).get("content").get(0).get("id").asLong();

        patchAs(asha, "/api/notifications/" + ravisNotification + "/read", Map.of())
                .andExpect(status().isNotFound());
    }

    @Test
    void anOpenStreamReceivesNewNotificationsAsTheyHappen() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");

        MvcResult stream = mvc.perform(get("/api/notifications/stream")
                        .header("Authorization", ravi.bearer())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        postAs(asha, "/api/teams/" + teamId + "/invitations", Map.of("email", ravi.email()))
                .andExpect(status().isCreated());

        String events = stream.getResponse().getContentAsString();
        assertThat(events)
                .contains("event:connected")
                .contains("event:notification")
                .contains("\"type\":\"TEAM_INVITATION\"")
                .contains("Asha invited you to join Platform");
    }

    @Test
    void theStreamNeedsAToken() throws Exception {
        mvc.perform(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
