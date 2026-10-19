package com.vikashini.taskmaster.auth;

import com.vikashini.taskmaster.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationTest extends IntegrationTest {

    @Test
    void registrationStoresTheEmailInLowerCaseAndNeverReturnsThePassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "  Asha@Example.COM ", "name", "Asha", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void anEmailCanOnlyBeRegisteredOnceWhateverItsCase() throws Exception {
        signUp("Asha");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "ASHA@example.com", "name", "Other", "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationValidatesEveryField() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "not-an-email", "name", " ", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void aPasswordLongerThanBcryptCanHashIsRejectedRatherThanTruncated() throws Exception {
        // 40 characters but 80 bytes in UTF-8.
        String tooLong = "é".repeat(40);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "a@example.com", "name", "A", "password", tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginReturnsABearerTokenThatOpensProtectedEndpoints() throws Exception {
        TestUser asha = signUp("Asha");

        getAs(asha, "/api/users/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(asha.id()))
                .andExpect(jsonPath("$.name").value("Asha"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        signUp("Asha");

        for (Map<String, String> attempt : List.of(
                Map.of("email", "asha@example.com", "password", "wrong-password"),
                Map.of("email", "nobody@example.com", "password", PASSWORD))) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(toJson(attempt)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/tasks").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyTheTokenThatWasUsed() throws Exception {
        TestUser asha = signUp("Asha");
        String otherSession = login(asha.email(), PASSWORD);

        postAs(asha, "/api/auth/logout", Map.of()).andExpect(status().isNoContent());

        getAs(asha, "/api/users/me").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + otherSession))
                .andExpect(status().isOk());
    }

    @Test
    void profileUpdatesOnlyChangeTheFieldsSent() throws Exception {
        TestUser asha = signUp("Asha");

        patchAs(asha, "/api/users/me", Map.of("bio", "Backend developer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.bio").value("Backend developer"));

        patchAs(asha, "/api/users/me", Map.of("name", "Asha K"))
                .andExpect(jsonPath("$.name").value("Asha K"))
                .andExpect(jsonPath("$.bio").value("Backend developer"));

        patchAs(asha, "/api/users/me", Map.of("name", "   ")).andExpect(status().isBadRequest());
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() throws Exception {
        TestUser asha = signUp("Asha");

        putAs(asha, "/api/users/me/password",
                Map.of("currentPassword", "wrong-password", "newPassword", "a-new-password"))
                .andExpect(status().isBadRequest());

        putAs(asha, "/api/users/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "a-new-password"))
                .andExpect(status().isNoContent());

        login(asha.email(), "a-new-password");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", asha.email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocumentationIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TaskMaster API"));
    }
}
