package com.sparkz.sparkz.acceptance;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MatchJourneyTests extends AcceptanceSupport {
    @Test void twoSignedInUsersDiscoverCancelDeclineAndAcceptThroughTheRealApi() throws Exception {
        as(A, get("/api/discovery?teaches=2&learns=" + JAVA + "&day=1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profiles.length()").value(1))
            .andExpect(jsonPath("$.profiles[0].userId").value(B.toString()))
            .andExpect(jsonPath("$.profiles[0].reciprocal").value(true));
        long canceled = send(A, B, JAVA);
        visible(A, B, false); visible(B, A, false);
        as(B, get("/api/match-requests?direction=incoming"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(canceled));
        transition(A, canceled, "Canceled").andExpect(status().isOk());
        storedStatus(canceled, "canceled");
        visible(A, B, true); visible(B, A, true);

        long declined = send(A, B, JAVA);
        transition(B, declined, "Declined").andExpect(status().isOk());
        storedStatus(declined, "declined");
        visible(A, B, true); visible(B, A, true);

        long accepted = send(B, A, 2);
        transition(A, accepted, "Accepted").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Accepted"));
        storedStatus(accepted, "accepted");
        visible(A, B, false); visible(B, A, false);
        as(A, get("/api/match-requests?direction=incoming"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("Accepted"));
        as(B, get("/api/match-requests?direction=outgoing"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("Accepted"));
        as(A, sendBody(B, JAVA)).andExpect(status().isConflict());
        transition(B, accepted, "Canceled").andExpect(status().isConflict());
    }

    @Test void signedIdentityControlsOwnershipAndUnrelatedUsersCannotSeeRequests() throws Exception {
        long request = send(A, B, JAVA);
        as(B, sendBody(A, 2)).andExpect(status().isConflict());
        transition(A, request, "Accepted").andExpect(status().isForbidden());
        transition(A, request, "Declined").andExpect(status().isForbidden());
        transition(B, request, "Canceled").andExpect(status().isForbidden());
        transition(C, request, "Accepted").andExpect(status().isNotFound());
        for (String direction : List.of("incoming", "outgoing")) {
            as(C, get("/api/match-requests?direction=" + direction))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        storedStatus(request, "pending");
    }

    @Test void missingForgedAndExpiredTokensCannotReadOrWrite() throws Exception {
        for (String token : List.of("malformed", token(A, Instant.now().minusSeconds(300)))) {
            mvc.perform(get("/api/discovery").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
            mvc.perform(sendBody(B, JAVA).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/discovery")).andExpect(status().isUnauthorized());
        mvc.perform(sendBody(B, JAVA)).andExpect(status().isUnauthorized());
        assertThat(db.sql("select count(*) from match_requests").query(Long.class).single()).isZero();
    }

    @Test void discoverySharesCatalogIdsAndExposesOnlyPublicProfileData() throws Exception {
        as(A, get("/api/discovery/skills"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(JAVA))
            .andExpect(jsonPath("$[0].description").value("Java programming"));
        as(A, get("/api/discovery"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profiles[0].bio").value("Bob public bio"))
            .andExpect(jsonPath("$.profiles[0].availableDays", contains(1)))
            .andExpect(jsonPath("$.profiles[0].email").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].address").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].phone").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].dob").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].unavailable").doesNotExist())
            .andExpect(content().string(not(containsString("bob@example.test"))))
            .andExpect(content().string(not(containsString("Private Bob address"))));
    }
}
