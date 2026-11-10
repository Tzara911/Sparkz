package com.sparkz.sparkz.profiles;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms GET/PUT /api/profiles/me only ever reads or writes the logged-in
 * user's own profile. Whoever is authenticated determines whose row is
 * affected - there's no parameter to target someone else's profile.
 *
 * @Transactional here means every test's database writes are rolled back
 * when the test method ends. That matters because the H2 database stays
 * open for the whole test run (see data.sql's comment on DB_CLOSE_DELAY),
 * so without a rollback, the update test could permanently change the demo
 * data and break other tests depending on test execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsTheAuthenticatedUsersOwnProfile() throws Exception {
        mockMvc
            .perform(get("/api/profiles/me").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(LocalLoginConfig.DEMO_USER_1))
            .andExpect(jsonPath("$.username").value("demo_teacher"))
            .andExpect(jsonPath("$.accountStatus").value("active"));
    }

    @Test
    void differentUsersGetDifferentProfiles() throws Exception {
        mockMvc
            .perform(get("/api/profiles/me").with(httpBasic(LocalLoginConfig.DEMO_USER_2, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(LocalLoginConfig.DEMO_USER_2))
            .andExpect(jsonPath("$.username").value("demo_learner"));
    }

    @Test
    void updatesAndReturnsTheAuthenticatedUsersOwnProfile() throws Exception {
        String updatedProfileJson =
            """
            {
              "username": "lee_updated",
              "email": "lee.updated@example.com",
              "firstName": "Lee",
              "lastName": "Learner",
              "schoolName": "Sparkz High",
              "gradeYear": "12",
              "address": "123 Main St",
              "phone": "555-1234",
              "dob": "2008-05-01",
              "timeZone": "America/Chicago",
              "bio": "I love Python.",
              "avatarUrl": "https://example.com/avatar.png"
            }
            """;

        mockMvc
            .perform(
                put("/api/profiles/me")
                    .with(httpBasic(LocalLoginConfig.DEMO_USER_2, "local-demo"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatedProfileJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("lee_updated"))
            .andExpect(jsonPath("$.bio").value("I love Python."));

        // Fetching again proves the change was actually saved, not just echoed back.
        mockMvc
            .perform(get("/api/profiles/me").with(httpBasic(LocalLoginConfig.DEMO_USER_2, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("lee_updated"));
    }

    @Test
    void updatingOwnProfileDoesNotAffectOtherUsers() throws Exception {
        String updatedProfileJson =
            """
            {
              "username": "teacher_updated",
              "email": "dana.updated@example.com",
              "firstName": "Dana",
              "lastName": "Teacher",
              "schoolName": "Sparkz High",
              "gradeYear": "12",
              "timeZone": "America/Chicago"
            }
            """;

        mockMvc
            .perform(
                put("/api/profiles/me")
                    .with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatedProfileJson))
            .andExpect(status().isOk());

        mockMvc
            .perform(get("/api/profiles/me").with(httpBasic(LocalLoginConfig.DEMO_USER_2, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("demo_learner"));
    }

    @Test
    void rejectsUpdateWithBlankUsername() throws Exception {
        String invalidProfileJson =
            """
            {
              "username": "",
              "email": "demo1@example.com"
            }
            """;

        mockMvc
            .perform(
                put("/api/profiles/me")
                    .with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidProfileJson))
            .andExpect(status().isBadRequest());
    }
}
