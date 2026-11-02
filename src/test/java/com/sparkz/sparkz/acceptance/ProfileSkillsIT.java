package com.sparkz.sparkz.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requires PR #25; all mutations go through its real API, without mocking services/repositories. */
class ProfileSkillsIT extends AcceptanceSupport {
    static String profileBody(String school) {
        return """
            {"userId":"%s", "username":"alice_updated", "email":"updated@example.test",
             "firstName":"Updated Alice", "lastName":"One", "schoolName":"%s",
             "gradeYear":"Senior", "address":"Private updated address", "phone":"555-0199",
             "dob":"2000-01-01", "timeZone":"America/Chicago", "bio":"Updated public bio",
             "avatarUrl":"https://example.test/avatar.png",
             "accountStatus":"deactivated", "onboardingCompleted":false}
            """.formatted(B, school);
    }

    @Test void ownProfileRoundTripsWithoutChangingOtherUsersOrProtectedEligibilityFields() throws Exception {
        as(A, put("/api/profiles/me").contentType(MediaType.APPLICATION_JSON).content(profileBody("Demo University")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(A.toString()))
            .andExpect(jsonPath("$.accountStatus").value("active"))
            .andExpect(jsonPath("$.onboardingCompleted").value(true));
        as(A, get("/api/profiles/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice_updated"))
            .andExpect(jsonPath("$.dob").value("2000-01-01"))
            .andExpect(jsonPath("$.timeZone").value("America/Chicago"));
        as(B, get("/api/profiles/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("bob"));
        as(B, get("/api/discovery")).andExpect(status().isOk())
            .andExpect(jsonPath("$.profiles[0].bio").value("Updated public bio"))
            .andExpect(jsonPath("$.profiles[0].email").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].address").doesNotExist());
        send(A, B, JAVA);
    }

    @Test void invalidProfileDoesNotPartiallyOverwriteAnExistingRow() throws Exception {
        as(A, put("/api/profiles/me").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"\",\"email\":\"invalid\",\"firstName\":\"Bad update\"}"))
            .andExpect(status().isBadRequest());
        as(A, get("/api/profiles/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.firstName").value("Alice"));
        as(new java.util.UUID(0, 99), get("/api/profiles/me")).andExpect(status().isNotFound());
    }

    @Test void bothSkillRolesAreIndependentIdempotentAndOwnedByTheVerifiedUser() throws Exception {
        String role = "/api/profiles/me/skills/" + JAVA + "/learn";
        as(A, put(role)).andExpect(status().isNoContent());
        as(A, put(role)).andExpect(status().isNoContent());
        as(A, get("/api/profiles/me/skills")).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.skillId == " + JAVA + ")].skillType",
                org.hamcrest.Matchers.containsInAnyOrder("teach", "learn")));
        assertThat(db.sql("select count(*) from user_skills where user_id = :id and skill_id = :skill")
            .param("id", A).param("skill", JAVA).query(Long.class).single()).isEqualTo(2);
        as(A, delete(role)).andExpect(status().isNoContent());
        as(A, delete(role)).andExpect(status().isNoContent());
        as(B, get("/api/profiles/me/skills")).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.skillId == " + JAVA + ")].skillType",
                org.hamcrest.Matchers.contains("learn")));
        as(A, get("/api/skills")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        send(A, B, JAVA); // A's teach association and B's learn association survived.
    }

    @Test void removingAndReaddingSkillsImmediatelyChangesRecommendationsAndRequestEligibility() throws Exception {
        visible(A, B, true);
        as(B, delete("/api/profiles/me/skills/" + JAVA + "/learn")).andExpect(status().isNoContent());
        as(B, delete("/api/profiles/me/skills/2/teach")).andExpect(status().isNoContent());
        visible(A, B, false); visible(B, A, false);
        as(A, sendBody(B, JAVA)).andExpect(status().isUnprocessableEntity());
        as(B, put("/api/profiles/me/skills/" + JAVA + "/learn")).andExpect(status().isNoContent());
        visible(A, B, true);
        long request = send(A, B, JAVA);
        as(B, delete("/api/profiles/me/skills/" + JAVA + "/learn")).andExpect(status().isNoContent());
        transition(B, request, "Accepted").andExpect(status().isUnprocessableEntity());
        storedStatus(request, "pending");
        transition(B, request, "Declined").andExpect(status().isOk());
        storedStatus(request, "declined");
    }

    @Test void changingSchoolAfterRecommendationInvalidatesTheStaleRequest() throws Exception {
        visible(A, B, true);
        as(A, put("/api/profiles/me").contentType(MediaType.APPLICATION_JSON).content(profileBody("Other University")))
            .andExpect(status().isOk());
        visible(B, A, false);
        as(A, sendBody(B, JAVA)).andExpect(status().isUnprocessableEntity());
        assertThat(db.sql("select count(*) from match_requests").query(Long.class).single()).isZero();
    }

    @Test void rejectsAnonymousSkillWritesAndInvalidSkillRolesWithoutChangingAssociations() throws Exception {
        mvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/profiles/me").contentType(MediaType.APPLICATION_JSON).content(profileBody("Demo University")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profiles/me/skills")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/profiles/me/skills/2/teach")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/profiles/me/skills/2/learn")).andExpect(status().isUnauthorized());
        as(A, put("/api/profiles/me/skills/2/teacher")).andExpect(status().isBadRequest());
        as(A, put("/api/profiles/me/skills/999/teach")).andExpect(status().isNotFound());
        assertThat(db.sql("select count(*) from user_skills").query(Long.class).single()).isEqualTo(4);
    }
}
