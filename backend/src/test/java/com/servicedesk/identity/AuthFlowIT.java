package com.servicedesk.identity;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.support.ApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AuthFlowIT extends ApiIntegrationTest {

    @Test
    void unauthenticatedRequestsGet401ProblemDetail() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void registerLoginMeLogout() throws Exception {
        mvc.perform(api.jsonBody(post("/api/auth/register").with(csrf()),
                        Map.of("email", "New@Example.com", "password", "secret123", "fullName", "New User")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.memberships", hasSize(0)));

        MockHttpSession session = api.login("new@example.com", "secret123");

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New User"));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIs401WithStableCode() throws Exception {
        data.user("someone");
        mvc.perform(api.jsonBody(post("/api/auth/login").with(csrf()),
                        Map.of("email", "someone1@test.local", "password", "nope-nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        var body = Map.of("email", "dup@example.com", "password", "secret123", "fullName", "Dup");
        mvc.perform(api.jsonBody(post("/api/auth/register").with(csrf()), body)).andExpect(status().isCreated());
        mvc.perform(api.jsonBody(post("/api/auth/register").with(csrf()), body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void validationErrorsListFields() throws Exception {
        mvc.perform(api.jsonBody(post("/api/auth/register").with(csrf()),
                        Map.of("email", "not-an-email", "password", "short", "fullName", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void mutationWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"email\":\"a@b.co\",\"password\":\"secret123\",\"fullName\":\"A\"}"))
                .andExpect(status().isForbidden());
    }
}
