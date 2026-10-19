package com.vikashini.taskmaster.support;

import com.vikashini.taskmaster.ai.TextGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that go through the whole stack: HTTP, security, services and
 * the real PostgreSQL test database. Tables are emptied before every test, so each one
 * starts from nothing and can run on its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.FakeAi.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected FakeTextGenerator ai;

    @BeforeEach
    void resetState() {
        List<String> tables = jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                 WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                """, String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
        ai.reset();
    }

    /** A registered, logged-in user. */
    protected record TestUser(long id, String email, String name, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected TestUser signUp(String name) throws Exception {
        String email = name.toLowerCase() + "@example.com";
        JsonNode user = body(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "name", name, "password", PASSWORD))))
                .andExpect(status().isCreated()));
        return new TestUser(user.get("id").asLong(), email, name, login(email, PASSWORD));
    }

    protected String login(String email, String password) throws Exception {
        return body(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()))
                .get("accessToken").asString();
    }

    // Request helpers: each sends JSON as the given user and returns the result for assertions.

    protected ResultActions getAs(TestUser user, String url) throws Exception {
        return mvc.perform(authed(get(url), user));
    }

    protected ResultActions postAs(TestUser user, String url, Object body) throws Exception {
        return mvc.perform(withJson(authed(post(url), user), body));
    }

    protected ResultActions putAs(TestUser user, String url, Object body) throws Exception {
        return mvc.perform(withJson(authed(put(url), user), body));
    }

    protected ResultActions patchAs(TestUser user, String url, Object body) throws Exception {
        return mvc.perform(withJson(authed(patch(url), user), body));
    }

    protected ResultActions deleteAs(TestUser user, String url) throws Exception {
        return mvc.perform(authed(delete(url), user));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        MvcResult mvcResult = result.andReturn();
        return json.readTree(mvcResult.getResponse().getContentAsString());
    }

    protected String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    // Shortcuts for setting up the situation a test needs.

    protected long createTask(TestUser user, Map<String, Object> fields) throws Exception {
        return body(postAs(user, "/api/tasks", fields).andExpect(status().isCreated())).get("id").asLong();
    }

    protected long createTeam(TestUser owner, String name) throws Exception {
        return body(postAs(owner, "/api/teams", Map.of("name", name)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    /** Invites {@code member} to the team and has them accept. */
    protected void addToTeam(TestUser owner, long teamId, TestUser member) throws Exception {
        long invitationId = body(postAs(owner, "/api/teams/" + teamId + "/invitations",
                Map.of("email", member.email())).andExpect(status().isCreated())).get("id").asLong();
        postAs(member, "/api/invitations/" + invitationId + "/accept", Map.of()).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, TestUser user) {
        return user == null ? request : request.header("Authorization", user.bearer());
    }

    private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
    }

    /** Replaces the Claude client in every test. */
    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        FakeTextGenerator fakeTextGenerator() {
            return new FakeTextGenerator();
        }
    }

    /** Records what it was asked and answers with fixed text, or fails when told to. */
    public static class FakeTextGenerator implements TextGenerator {

        private volatile String lastInstructions;
        private volatile String lastInput;
        private volatile RuntimeException failure;

        @Override
        public String generate(String instructions, String input) {
            this.lastInstructions = instructions;
            this.lastInput = input;
            if (failure != null) {
                throw failure;
            }
            return "Generated text";
        }

        public String lastInstructions() {
            return lastInstructions;
        }

        public String lastInput() {
            return lastInput;
        }

        public void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        void reset() {
            lastInstructions = null;
            lastInput = null;
            failure = null;
        }
    }
}
