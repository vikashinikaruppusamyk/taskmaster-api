package com.vikashini.taskmaster.task;

import com.vikashini.taskmaster.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskTest extends IntegrationTest {

    @Test
    void createsAPersonalTaskWithDefaults() throws Exception {
        TestUser asha = signUp("Asha");

        postAs(asha, "/api/tasks", Map.of("title", "  Write the README  ", "description", "Setup and examples"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Write the README"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.team").value(nullValue()))
                .andExpect(jsonPath("$.createdBy.id").value(asha.id()))
                .andExpect(jsonPath("$.assignee").value(nullValue()))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void aTaskPastItsDueDateIsOverdueUntilItIsCompleted() throws Exception {
        TestUser asha = signUp("Asha");
        long id = createTask(asha, Map.of("title", "Late", "dueDate", LocalDate.now().minusDays(1).toString()));

        getAs(asha, "/api/tasks/" + id).andExpect(jsonPath("$.overdue").value(true));
        patchAs(asha, "/api/tasks/" + id + "/status", Map.of("status", "COMPLETED"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void completingATaskRecordsWhenAndReopeningClearsIt() throws Exception {
        TestUser asha = signUp("Asha");
        long id = createTask(asha, Map.of("title", "Ship it"));

        patchAs(asha, "/api/tasks/" + id + "/status", Map.of("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()));

        patchAs(asha, "/api/tasks/" + id + "/status", Map.of("status", "OPEN"))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));

        patchAs(asha, "/api/tasks/" + id + "/status", Map.of("status", "DONE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReplacesTheEditableFields() throws Exception {
        TestUser asha = signUp("Asha");
        long id = createTask(asha, Map.of("title", "Draft", "description", "old", "dueDate", "2030-01-01"));

        putAs(asha, "/api/tasks/" + id, Map.of("title", "Final", "priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.dueDate").value(nullValue()));

        putAs(asha, "/api/tasks/" + id, Map.of("title", "No priority"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priority").exists());
    }

    @Test
    void filtersByStatusAndPriority() throws Exception {
        TestUser asha = signUp("Asha");
        createTask(asha, Map.of("title", "Open low", "priority", "LOW"));
        long done = createTask(asha, Map.of("title", "Done high", "priority", "HIGH"));
        createTask(asha, Map.of("title", "Open high", "priority", "HIGH"));
        patchAs(asha, "/api/tasks/" + done + "/status", Map.of("status", "COMPLETED"));

        getAs(asha, "/api/tasks?status=OPEN&sort=title,asc")
                .andExpect(jsonPath("$.content[*].title", contains("Open high", "Open low")));
        getAs(asha, "/api/tasks?status=COMPLETED")
                .andExpect(jsonPath("$.content[*].title", contains("Done high")));
        getAs(asha, "/api/tasks?status=OPEN&status=COMPLETED&priority=HIGH&sort=title,asc")
                .andExpect(jsonPath("$.content[*].title", contains("Done high", "Open high")));
    }

    @Test
    void searchLooksInTitleAndDescriptionIgnoringCase() throws Exception {
        TestUser asha = signUp("Asha");
        createTask(asha, Map.of("title", "Fix LOGIN bug"));
        createTask(asha, Map.of("title", "Docs", "description", "Explain the login flow"));
        createTask(asha, Map.of("title", "Unrelated"));

        getAs(asha, "/api/tasks?q=login&sort=title,asc")
                .andExpect(jsonPath("$.content[*].title", contains("Docs", "Fix LOGIN bug")));
    }

    @Test
    void searchTreatsWildcardCharactersLiterally() throws Exception {
        TestUser asha = signUp("Asha");
        createTask(asha, Map.of("title", "Reach 100% coverage"));
        createTask(asha, Map.of("title", "Reach 1000 users"));

        // Passed as a parameter: MockMvc would encode a literal % in the URL a second time.
        mvc.perform(get("/api/tasks").param("q", "100%").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Reach 100% coverage"));
        mvc.perform(get("/api/tasks").param("q", "_").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortsByPriorityInItsNaturalOrderNotAlphabetically() throws Exception {
        TestUser asha = signUp("Asha");
        createTask(asha, Map.of("title", "medium", "priority", "MEDIUM"));
        createTask(asha, Map.of("title", "high", "priority", "HIGH"));
        createTask(asha, Map.of("title", "low", "priority", "LOW"));

        getAs(asha, "/api/tasks?sort=priority,desc")
                .andExpect(jsonPath("$.content[*].title", contains("high", "medium", "low")));
    }

    @Test
    void sortsByDueDate() throws Exception {
        TestUser asha = signUp("Asha");
        createTask(asha, Map.of("title", "later", "dueDate", "2030-06-01"));
        createTask(asha, Map.of("title", "sooner", "dueDate", "2030-01-01"));

        getAs(asha, "/api/tasks?sort=dueDate,asc")
                .andExpect(jsonPath("$.content[*].title", contains("sooner", "later")));
        getAs(asha, "/api/tasks?dueFrom=2030-03-01")
                .andExpect(jsonPath("$.content[*].title", contains("later")));
    }

    @Test
    void refusesToSortByUnknownFields() throws Exception {
        TestUser asha = signUp("Asha");

        getAs(asha, "/api/tasks?sort=passwordHash")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Cannot sort by 'passwordHash'")));
    }

    @Test
    void paginates() throws Exception {
        TestUser asha = signUp("Asha");
        for (int i = 1; i <= 5; i++) {
            createTask(asha, Map.of("title", "Task " + i));
        }

        getAs(asha, "/api/tasks?size=2&page=2&sort=title,asc")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Task 5"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void otherPeoplesPersonalTasksLookLikeTheyDoNotExist() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long id = createTask(asha, Map.of("title", "Private"));

        getAs(ravi, "/api/tasks/" + id).andExpect(status().isNotFound());
        putAs(ravi, "/api/tasks/" + id, Map.of("title", "Hacked", "priority", "LOW")).andExpect(status().isNotFound());
        deleteAs(ravi, "/api/tasks/" + id).andExpect(status().isNotFound());
        getAs(ravi, "/api/tasks").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aPersonalTaskCanOnlyBeAssignedToItsCreator() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");

        postAs(asha, "/api/tasks", Map.of("title", "Mine", "assigneeId", ravi.id()))
                .andExpect(status().isBadRequest());

        long id = createTask(asha, Map.of("title", "Mine", "assigneeId", asha.id()));
        getAs(asha, "/api/tasks?assignedToMe=true")
                .andExpect(jsonPath("$.content[*].id", contains((int) id)));
    }

    @Test
    void creatorCanDeleteATask() throws Exception {
        TestUser asha = signUp("Asha");
        long id = createTask(asha, Map.of("title", "Temporary"));

        deleteAs(asha, "/api/tasks/" + id).andExpect(status().isNoContent());
        getAs(asha, "/api/tasks/" + id).andExpect(status().isNotFound());
    }

    @Test
    void creatingATaskInATeamYouAreNotInIsRefused() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Core");

        postAs(ravi, "/api/tasks", Map.of("title", "Sneaky", "teamId", teamId)).andExpect(status().isNotFound());
    }
}
