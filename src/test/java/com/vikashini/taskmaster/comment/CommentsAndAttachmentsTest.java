package com.vikashini.taskmaster.comment;

import com.vikashini.taskmaster.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentsAndAttachmentsTest extends IntegrationTest {

    @Test
    void teamMembersDiscussATaskAndTheCreatorIsNotified() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        long taskId = createTask(asha, Map.of("title", "Pick a database", "teamId", teamId));
        String comments = "/api/tasks/" + taskId + "/comments";

        postAs(ravi, comments, Map.of("body", "PostgreSQL?")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.name").value("Ravi"))
                .andExpect(jsonPath("$.edited").value(false));
        postAs(asha, comments, Map.of("body", "Agreed")).andExpect(status().isCreated());

        getAs(asha, comments).andExpect(jsonPath("$[*].body", contains("PostgreSQL?", "Agreed")));
        getAs(asha, "/api/notifications")
                .andExpect(jsonPath("$.content[0].type").value("TASK_COMMENTED"))
                .andExpect(jsonPath("$.content[0].message").value("Ravi commented on \"Pick a database\""));
    }

    @Test
    void onlyTheAuthorEditsAComment() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser ravi = signUp("Ravi");
        long teamId = createTeam(asha, "Platform");
        addToTeam(asha, teamId, ravi);
        long taskId = createTask(asha, Map.of("title", "T", "teamId", teamId));
        long commentId = body(postAs(ravi, "/api/tasks/" + taskId + "/comments", Map.of("body", "First")))
                .get("id").asLong();
        String comment = "/api/tasks/" + taskId + "/comments/" + commentId;

        putAs(asha, comment, Map.of("body", "Changed by Asha")).andExpect(status().isForbidden());
        putAs(ravi, comment, Map.of("body", "Second thoughts"))
                .andExpect(jsonPath("$.body").value("Second thoughts"))
                .andExpect(jsonPath("$.edited").value(true));
        // The task's creator may tidy up comments on it.
        deleteAs(asha, comment).andExpect(status().isNoContent());
        getAs(ravi, "/api/tasks/" + taskId + "/comments").andExpect(content().json("[]"));
    }

    @Test
    void commentsAreValidatedAndHiddenFromOutsiders() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser outsider = signUp("Outsider");
        long taskId = createTask(asha, Map.of("title", "Private"));

        postAs(asha, "/api/tasks/" + taskId + "/comments", Map.of("body", " ")).andExpect(status().isBadRequest());
        postAs(outsider, "/api/tasks/" + taskId + "/comments", Map.of("body", "hi")).andExpect(status().isNotFound());
        getAs(outsider, "/api/tasks/" + taskId + "/comments").andExpect(status().isNotFound());
    }

    @Test
    void uploadsListsAndDownloadsAnAttachment() throws Exception {
        TestUser asha = signUp("Asha");
        long taskId = createTask(asha, Map.of("title", "Spec"));
        byte[] bytes = "line one\nline two\n".getBytes(StandardCharsets.UTF_8);

        long id = body(upload(asha, taskId, new MockMultipartFile("file", "notes.txt", "text/plain", bytes))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("notes.txt"))
                .andExpect(jsonPath("$.sizeBytes").value(bytes.length))
                .andExpect(jsonPath("$.uploadedBy.id").value(asha.id()))).get("id").asLong();

        getAs(asha, "/api/tasks/" + taskId + "/attachments").andExpect(jsonPath("$[*].id", contains((int) id)));
        getAs(asha, "/api/tasks/" + taskId + "/attachments/" + id + "/download")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("notes.txt")))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void aFileNameCannotPointOutsideTheStorageFolder() throws Exception {
        TestUser asha = signUp("Asha");
        long taskId = createTask(asha, Map.of("title", "Spec"));

        upload(asha, taskId, new MockMultipartFile("file", "..\\..\\etc/passwd", "text/plain", new byte[] {1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passwd"));

        String key = jdbc.queryForObject("SELECT storage_key FROM attachments", String.class);
        assertThat(key).matches("[0-9a-f-]{36}");
    }

    @Test
    void deletingAnAttachmentRemovesTheFileToo() throws Exception {
        TestUser asha = signUp("Asha");
        long taskId = createTask(asha, Map.of("title", "Spec"));
        long id = body(upload(asha, taskId, new MockMultipartFile("file", "a.bin", null, new byte[] {1, 2, 3})))
                .get("id").asLong();
        Path file = storedFile();
        assertThat(file).exists();

        deleteAs(asha, "/api/tasks/" + taskId + "/attachments/" + id).andExpect(status().isNoContent());

        assertThat(file).doesNotExist();
        getAs(asha, "/api/tasks/" + taskId + "/attachments").andExpect(content().json("[]"));
    }

    @Test
    void deletingATaskRemovesItsAttachmentFiles() throws Exception {
        TestUser asha = signUp("Asha");
        long taskId = createTask(asha, Map.of("title", "Spec"));
        upload(asha, taskId, new MockMultipartFile("file", "a.bin", null, new byte[] {1}));
        Path file = storedFile();

        deleteAs(asha, "/api/tasks/" + taskId).andExpect(status().isNoContent());

        assertThat(file).doesNotExist();
    }

    @Test
    void attachmentsAreCheckedAndHiddenFromOutsiders() throws Exception {
        TestUser asha = signUp("Asha");
        TestUser outsider = signUp("Outsider");
        long taskId = createTask(asha, Map.of("title", "Private"));

        upload(asha, taskId, new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .andExpect(status().isBadRequest());
        upload(outsider, taskId, new MockMultipartFile("file", "x.txt", "text/plain", new byte[] {1}))
                .andExpect(status().isNotFound());
    }

    private ResultActions upload(TestUser user, long taskId, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/tasks/" + taskId + "/attachments").file(file)
                .header("Authorization", user.bearer()));
    }

    /** Where the single stored attachment lives on disk. */
    private Path storedFile() {
        String key = jdbc.queryForObject("SELECT storage_key FROM attachments", String.class);
        return Path.of(System.getProperty("java.io.tmpdir"), "taskmaster-test-uploads", key);
    }
}
