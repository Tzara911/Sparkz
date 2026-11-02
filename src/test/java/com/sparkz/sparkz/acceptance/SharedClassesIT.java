package com.sparkz.sparkz.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requires PR #24's actual classes on the source tree. Run explicitly with -Dtest=SharedClassesIT. */
class SharedClassesIT extends AcceptanceSupport {
    @Test void profileEditsUseJwtIdentityAndPreserveMatchingEligibility() throws Exception {
        as(A, get("/api/users/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(A.toString()));
        as(A, put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("""
            {"userId":"%s", "firstName":"Updated Alice", "lastName":"One",
             "email":"updated@example.test", "bio":"Edited through PR24"}
            """.formatted(B)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(A.toString()));
        as(B, get("/api/users/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Bob"))
            .andExpect(jsonPath("$.email").value("bob@example.test"));
        as(A, get("/api/users/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Updated Alice"));
        assertThat(db.sql("select school_name from profiles where user_id = :id").param("id", A)
            .query(String.class).single()).isEqualTo("Demo University");
        visible(B, A, true);
        long request = send(A, B, JAVA);
        transition(B, request, "Accepted").andExpect(status().isOk());
        storedStatus(request, "accepted");
    }

    @Test void sharedJpaCatalogReadsSupabaseNamesDescriptionsAndBigintIds() throws Exception {
        as(A, get("/api/skills/" + JAVA)).andExpect(status().isOk())
            .andExpect(jsonPath("$.skillId").value(JAVA))
            .andExpect(jsonPath("$.skillName").value("Java"))
            .andExpect(jsonPath("$.description").value("Java programming"));
        as(A, get("/api/skills/2")).andExpect(status().isOk())
            .andExpect(jsonPath("$.description").isEmpty());
        as(A, get("/api/skills/999")).andExpect(status().isNotFound());
        as(A, get("/api/skills")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        as(A, get("/api/discovery/skills")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(JAVA));
        as(A, post("/api/skills").contentType(MediaType.APPLICATION_JSON)
            .content("{\"skillName\":\"Unauthorized catalog edit\"}"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test void profileDeletionUsesCurrentIdentityAndRespectsFixtureForeignKeys() throws Exception {
        send(A, B, JAVA);
        as(A, delete("/api/users/me")).andExpect(status().isNoContent());
        as(A, get("/api/users/me")).andExpect(status().isNotFound());
        as(B, get("/api/users/me")).andExpect(status().isOk());
        assertThat(db.sql("select count(*) from user_skills where user_id = :id")
            .param("id", A).query(Long.class).single()).isZero();
        assertThat(db.sql("select count(*) from match_requests").query(Long.class).single()).isZero();
        as(B, get("/api/skills/" + JAVA)).andExpect(status().isOk());
        // This endpoint deletes a profile, not a Supabase Auth account.
    }

    @Test void unauthenticatedUsersCannotReadEditOrDeleteProfilesAndCatalog() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/skills")).andExpect(status().isUnauthorized());
        as(new java.util.UUID(0, 99), get("/api/users/me")).andExpect(status().isNotFound());
    }
}
