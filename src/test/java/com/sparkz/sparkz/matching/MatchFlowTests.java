package com.sparkz.sparkz.matching;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:match-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchFlowTests {
    static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");
    @Autowired MatchService service;
    @Autowired JdbcClient db;
    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() {
        db.sql("delete from match_requests").update();
        db.sql("delete from blocked_users").update();
        db.sql("delete from user_skills").update();
        db.sql("update profiles set account_status = 'Active', onboarding_completed = true, school_name = 'Demo University'").update();
        db.sql("insert into user_skills values (:a, 1, 'Teaching'), (:b, 1, 'Learning')")
            .param("a", A).param("b", B).update();
    }

    @Test void sendListAndAccept() throws Exception {
        mvc.perform(post("/api/match-requests").with(user(A.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiverId\":\"" + B + "\",\"skillId\":1}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("Pending"));
        MatchRequest request = service.list(A, false).get(0);
        mvc.perform(get("/api/match-requests?direction=incoming").with(user(B.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].senderId").value(A.toString()));
        assertThat(service.list(A, true)).isEmpty();
        assertThat(service.list(C, false)).isEmpty();
        mvc.perform(patch("/api/match-requests/" + request.id()).with(user(B.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Accepted\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Accepted"));
        assertThat(service.list(B, true).get(0).status()).isEqualTo(MatchStatus.Accepted);
        expect(409, () -> service.send(B, A, 1));
    }

    @Test void terminalStatesAndNewRequests() {
        MatchRequest first = service.send(A, B, 1);
        service.change(B, first.id(), MatchStatus.Declined);
        expect(409, () -> service.change(A, first.id(), MatchStatus.Canceled));
        MatchRequest second = service.send(B, A, 1);
        service.change(B, second.id(), MatchStatus.Canceled);
        assertThat(service.send(A, B, 1).status()).isEqualTo(MatchStatus.Pending);
    }

    @Test void rejectsDuplicatesInBothDirections() {
        service.send(A, B, 1);
        expect(409, () -> service.send(A, B, 1));
        expect(409, () -> service.send(B, A, 1));
    }

    @Test void rolesAndNonparticipantsCannotChangeRequests() {
        MatchRequest r = service.send(A, B, 1);
        expect(403, () -> service.change(A, r.id(), MatchStatus.Accepted));
        expect(403, () -> service.change(A, r.id(), MatchStatus.Declined));
        expect(403, () -> service.change(B, r.id(), MatchStatus.Canceled));
        expect(404, () -> service.change(C, r.id(), MatchStatus.Accepted));
        expect(400, () -> service.change(B, r.id(), MatchStatus.Pending));
        assertThat(service.list(A, false).get(0).status()).isEqualTo(MatchStatus.Pending);
    }

    @Test void rejectsSelfUnknownAndIncompatibleSkill() {
        expect(400, () -> service.send(A, A, 1));
        expect(404, () -> service.send(A, C, 1));
        expect(422, () -> service.send(A, B, 2));
        db.sql("delete from user_skills where user_id = :b").param("b", B).update();
        expect(422, () -> service.send(A, B, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"account_status = 'Inactive'", "onboarding_completed = false", "school_name = 'Other School'", "school_name = null"})
    void rejectsIneligibleAccounts(String mutation) {
        db.sql("update profiles set " + mutation + " where user_id = :b").param("b", B).update();
        expect(422, () -> service.send(A, B, 1));
        expect(422, () -> service.send(B, A, 1));
    }

    @Test void rejectsUnsupportedSameSchool() {
        db.sql("update profiles set school_name = 'Unsupported'").update();
        expect(422, () -> service.send(A, B, 1));
    }

    @Test void blockedBothDirectionsAndRevalidatedOnAccept() {
        MatchRequest request = service.send(A, B, 1);
        db.sql("insert into blocked_users(blocker_id, blocked_id) values (:a, :b)").param("a", B).param("b", A).update();
        expect(422, () -> service.change(B, request.id(), MatchStatus.Accepted));
        service.change(A, request.id(), MatchStatus.Canceled);
        expect(422, () -> service.send(A, B, 1));
        expect(422, () -> service.send(B, A, 1));
    }

    @Test void eligibilityChangesDoNotPreventDeclineOrCancel() {
        MatchRequest r = service.send(A, B, 1);
        db.sql("update profiles set account_status = 'Inactive' where user_id = :b").param("b", B).update();
        expect(422, () -> service.change(B, r.id(), MatchStatus.Accepted));
        assertThat(service.change(B, r.id(), MatchStatus.Declined).status()).isEqualTo(MatchStatus.Declined);
    }

    @Test void validatesHttpInputAndAuthentication() throws Exception {
        mvc.perform(get("/api/match-requests?direction=incoming")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/match-requests").with(user(A.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/match-requests").with(user(A.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiverId\":\""+B+"\",\"skillId\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/match-requests?direction=invalid").with(user(A.toString())))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/match-requests/999").with(user(B.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Accepted\"}"))
            .andExpect(status().isNotFound());
    }

    @Test void concurrentOppositeSendsCreateOnlyOnePendingRequest() throws Exception {
        List<Integer> results = race(() -> { service.send(A, B, 1); return 201; },
                                     () -> { service.send(B, A, 1); return 201; });
        assertThat(results).containsExactlyInAnyOrder(201, 409);
        assertThat(db.sql("select count(*) from match_requests").query(Long.class).single()).isEqualTo(1);
    }

    @Test void concurrentAcceptAndCancelHaveOnlyOneWinner() throws Exception {
        MatchRequest r = service.send(A, B, 1);
        List<Integer> results = race(() -> { service.change(B, r.id(), MatchStatus.Accepted); return 200; },
                                     () -> { service.change(A, r.id(), MatchStatus.Canceled); return 200; });
        assertThat(results).containsExactlyInAnyOrder(200, 409);
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = Stream.of(first, second).map(task -> executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { return task.call(); }
                catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            })).toList();
            start.countDown();
            return List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    private void expect(int status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(status));
    }
}
