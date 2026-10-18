package com.vikashini.taskmaster.ai;

import com.vikashini.taskmaster.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The AI endpoints, with the model replaced by a fake that records what it was sent. */
class AiAssistanceTest extends IntegrationTest {

    @Test
    void draftsADescriptionFromTheTitleAndNotes() throws Exception {
        TestUser asha = signUp("Asha");

        postAs(asha, "/api/ai/task-description", Map.of("title", "Set up CI", "notes", "GitHub Actions, run tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Generated text"));

        assertThat(ai.lastInput()).contains("<title>Set up CI</title>").contains("GitHub Actions, run tests");
        assertThat(ai.lastInstructions()).contains("task description");
    }

    @Test
    void summarisesATaskWithItsComments() throws Exception {
        TestUser asha = signUp("Asha");
        long taskId = createTask(asha, Map.of("title", "Choose a database", "description", "For the API"));
        postAs(asha, "/api/tasks/" + taskId + "/comments", Map.of("body", "Going with PostgreSQL"));

        postAs(asha, "/api/ai/tasks/" + taskId + "/summary", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Generated text"));

        assertThat(ai.lastInput())
                .contains("Title: Choose a database")
                .contains("Description: For the API")
                .contains("Asha")
                .contains("Going with PostgreSQL");
    }

    @Test
    void cannotSummariseATaskYouCannotSee() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser outsider = signUp("Outsider");
        long taskId = createTask(asha, Map.of("title", "Private"));

        postAs(outsider, "/api/ai/tasks/" + taskId + "/summary", Map.of()).andExpect(status().isNotFound());
        assertThat(ai.lastInput()).isNull();
    }

    @Test
    void reportsTheServiceAsUnavailableWhenTheModelCannotAnswer() throws Exception {
        TestUser asha = signUp("Asha");
        ai.failWith(new AiUnavailableException("AI features are off; set ANTHROPIC_API_KEY to turn them on"));

        postAs(asha, "/api/ai/task-description", Map.of("title", "Anything"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("AI features are off; set ANTHROPIC_API_KEY to turn them on"));
    }

    @Test
    void validatesTheInput() throws Exception {
        TestUser asha = signUp("Asha");

        postAs(asha, "/api/ai/task-description", Map.of("title", "")).andExpect(status().isBadRequest());
        assertThat(ai.lastInput()).isNull();
    }
}
