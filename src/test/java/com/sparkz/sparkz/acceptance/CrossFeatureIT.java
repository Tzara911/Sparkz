package com.sparkz.sparkz.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requires #21/#23/#24/#25 together. Startup collisions are failures, never skipped or hidden. */
class CrossFeatureIT extends AcceptanceSupport {
    @Test void bothProfileApisSeeTheSameDatabaseRecordAndKeepDiscoveryWorking() throws Exception {
        as(A, put("/api/profiles/me").contentType(MediaType.APPLICATION_JSON)
            .content(ProfileSkillsIT.profileBody("Demo University"))).andExpect(status().isOk());
        as(A, get("/api/users/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Updated Alice"))
            .andExpect(jsonPath("$.bio").value("Updated public bio"));
        as(A, put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"Alice via shared classes", "lastName":"One",
             "email":"updated@example.test", "bio":"Shared record"}
            """)).andExpect(status().isOk());
        as(A, get("/api/profiles/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Alice via shared classes"))
            .andExpect(jsonPath("$.schoolName").value("Demo University"))
            .andExpect(jsonPath("$.onboardingCompleted").value(true));
        visible(B, A, true);
    }

    @Test void sharedCatalogUserSkillsDiscoveryAndAcceptedRequestUseTheSameIds() throws Exception {
        as(C, get("/api/skills/" + JAVA)).andExpect(status().isOk())
            .andExpect(jsonPath("$.skillId").value(JAVA));
        as(C, put("/api/profiles/me/skills/" + JAVA + "/learn")).andExpect(status().isNoContent());
        visible(C, A, true);
        long request = send(C, A, JAVA);
        transition(A, request, "Accepted").andExpect(status().isOk());
        storedStatus(request, "accepted");
        visible(C, A, false); visible(A, C, false);
        as(B, get("/api/match-requests?direction=incoming"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
