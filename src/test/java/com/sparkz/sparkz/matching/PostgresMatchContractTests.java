package com.sparkz.sparkz.matching;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import com.sparkz.sparkz.availability.AvailabilityService;
import com.sparkz.sparkz.discovery.DiscoveryService;
import com.sparkz.sparkz.sessions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in real PostgreSQL checks. Creates tables only in a disposable loopback test DB. */
@EnabledIfEnvironmentVariable(named = "SPARKZ_POSTGRES_TEST_URL",
    matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sparkz_test")
@SpringBootTest(properties = {
    "spring.datasource.url=${SPARKZ_POSTGRES_TEST_URL}",
    "spring.datasource.username=${SPARKZ_POSTGRES_TEST_USERNAME:postgres}",
    "spring.datasource.password=${SPARKZ_POSTGRES_TEST_PASSWORD}",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.sql.init.schema-locations=classpath:db/postgres-match-schema.sql",
    "spring.sql.init.data-locations=",
    "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PostgresMatchContractTests {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    @Autowired MatchOperations operations;
    @Autowired JdbcClient db;
    @Autowired AvailabilityService availability;
    @Autowired DiscoveryService discovery;
    @Autowired MockMvc mvc;
    @Autowired LearningSessionService sessions;

    @BeforeEach void reset() {
        db.sql("delete from learning_sessions").update();
        db.sql("delete from match_requests").update();
        db.sql("delete from blocked_users").update();
        db.sql("delete from user_skills").update();
        db.sql("alter table match_requests alter column request_id restart with 1").update();
        db.sql("insert into user_skills values (:a, 1, 'teach'), (:b, 1, 'learn')")
            .param("a", A).param("b", B).update();
    }

    @Test void acceptsDatabaseDefaultRowsAndPersistsLatestStatus() {
        db.sql("insert into match_requests(sender_id, receiver_id, skill_id) values (:a, :b, 1)")
            .param("a", A).param("b", B).update();
        var pending = operations.list(B, true).get(0);
        assertThat(pending.status()).isEqualTo(MatchStatus.Pending);
        assertThat(pending.createdAt()).isNotNull();
        operations.acceptRequest(B, pending.id());
        assertThat(db.sql("select status from match_requests").query(String.class).single()).isEqualTo("accepted");
        assertThat(operations.list(A, false).get(0).status()).isEqualTo(MatchStatus.Accepted);
        expect(409, () -> operations.sendRequest(B, A, 1));
        assertThatThrownBy(() -> db.sql("update match_requests set status = 'accept'").update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void preservesBigintRequestIdentityWithoutNarrowing() {
        db.sql("alter table match_requests alter column request_id restart with 2147483648").update();
        var pending = operations.sendRequest(A, B, 1);
        assertThat(pending.id()).isEqualTo(2147483648L);
        operations.cancelRequest(A, pending.id());
        assertThat(operations.list(A, false).get(0).id()).isEqualTo(pending.id());
        assertThat(operations.list(A, false).get(0).status()).isEqualTo(MatchStatus.Canceled);
    }

    @Test void preservesCompositeRolesAndDatabaseForeignKeys() {
        db.sql("insert into user_skills values (:a, 1, 'learn')").param("a", A).update();
        assertThat(operations.sendRequest(A, B, 1).status()).isEqualTo(MatchStatus.Pending);
        assertThat(db.sql("select count(*) from user_skills where user_id = :a").param("a", A)
            .query(Integer.class).single()).isEqualTo(2);
        assertThatThrownBy(() -> db.sql("insert into user_skills values (:a, 1, 'teacher')").param("a", A).update())
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.sql("insert into profiles(user_id) values (:id)").param("id", new UUID(0, 99)).update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void concurrentOppositeSendsRespectPostgresProfileLocks() throws Exception {
        assertThat(race(() -> operations.sendRequest(A, B, 1), () -> operations.sendRequest(B, A, 1)))
            .containsExactlyInAnyOrder(200, 409);
        assertThat(db.sql("select count(*) from match_requests").query(Integer.class).single()).isEqualTo(1);
    }

    @Test void competingTerminalUpdatesHaveOneWinnerOnPostgres() throws Exception {
        var pending = operations.sendRequest(A, B, 1);
        assertThat(race(() -> operations.acceptRequest(B, pending.id()), () -> operations.cancelRequest(A, pending.id())))
            .containsExactlyInAnyOrder(200, 409);
    }

    @Test @Transactional
    void availabilityPreservesPostgresTimesOwnershipAndCompositeDays() {
        availability.replaceDays(A, List.of(1, 3));
        var first = availability.saveSlot(A, null, 1, LocalTime.of(9, 15), LocalTime.of(10, 45));
        var retained = availability.saveSlot(A, null, 3, LocalTime.of(14, 0), LocalTime.of(15, 30));
        assertThat(availability.own(A).slots()).containsExactly(first, retained);
        assertThat(availability.own(B).slots()).isEmpty();
        expect(404, () -> availability.saveSlot(B, first.slotId(), 1, LocalTime.NOON, LocalTime.of(13, 0)));
        expect(404, () -> availability.deleteSlot(B, first.slotId()));
        expect(400, () -> availability.saveSlot(A, null, 1, LocalTime.NOON, LocalTime.NOON));
        var changed = availability.saveSlot(A, retained.slotId(), 3, LocalTime.of(16, 0), LocalTime.of(17, 0));
        assertThat(availability.replaceDays(A, List.of(3)).slots()).containsExactly(changed);
        availability.deleteSlot(A, changed.slotId());
        assertThat(availability.own(A).slots()).isEmpty();
    }

    @Test @Transactional
    void discoveryUsesPostgresRolesAndDaysWithoutExposingPrivateData() throws Exception {
        long guitar = db.sql("insert into skills(skill_name) values ('Guitar') returning skill_id").query(Long.class).single();
        db.sql("insert into user_skills values (:a, :skill, 'learn'), (:b, :skill, 'teach')")
            .param("a", A).param("b", B).param("skill", guitar).update();
        availability.replaceDays(A, List.of(1, 3));
        availability.replaceDays(B, List.of(3, 5));
        availability.saveSlot(B, null, 3, LocalTime.of(15, 0), LocalTime.of(16, 0));
        db.sql("update profiles set email = 'private@example.com', phone = 'private-phone' where user_id = :b")
            .param("b", B).update();
        var results = discovery.browse(A, guitar, DiscoveryService.SkillType.teach, 3, true, 0, 20);
        assertThat(results.total()).isEqualTo(1);
        var person = results.profiles().get(0);
        assertThat(person.userId()).isEqualTo(B);
        assertThat(person.reciprocal()).isTrue();
        assertThat(person.commonDays()).containsExactly(3);
        String body = mvc.perform(get("/api/discovery/" + B).with(user(A.toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableDays[0]").value(3))
            .andExpect(jsonPath("$.slots").doesNotExist())
            .andExpect(jsonPath("$.email").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("private@example.com", "private-phone", "startTime", "endTime");
        db.sql("insert into blocked_users(blocker_id, blocked_id) values (:b, :a)")
            .param("a", A).param("b", B).update();
        assertThat(discovery.browse(A, null, null, null, false, 0, 20).profiles()).isEmpty();
    }

    @Test void learningSessionsRoundTripPostgresIdentityRolesOffsetsAndNullableFields() {
        var match = operations.sendRequest(A,B,1);
        operations.acceptRequest(B,match.id());
        db.sql("alter table learning_sessions alter column session_id restart with 2147483648").update();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(-6)).plusDays(2).truncatedTo(ChronoUnit.MICROS);
        var online = sessions.propose(B,new LearningSessionController.Propose(match.id(),A,start,start.plusHours(1),
            SessionMode.ONLINE,null,"https://meet.example.test/room",null));
        assertThat(online.id()).isGreaterThan(Integer.MAX_VALUE);
        assertThat(online.scheduledStart()).isEqualTo(start.toInstant());
        assertThat(online.scheduledEnd()).isEqualTo(start.plusHours(1).toInstant());
        assertThat(online.proposedBy()).isEqualTo(B);
        assertThat(online.teacherId()).isEqualTo(A);
        assertThat(online.learnerId()).isEqualTo(B);
        assertThat(online.location()).isNull();
        assertThat(online.message()).isNull();
        assertThat(online.createdAt()).isNotNull();
        assertThat(sessions.change(A,online.id(),SessionStatus.ACCEPTED).status()).isEqualTo(SessionStatus.ACCEPTED);
        assertThat(sessions.change(B,online.id(),SessionStatus.CANCELED).status()).isEqualTo(SessionStatus.CANCELED);
        assertThat(db.sql("select status from learning_sessions where session_id=:id").param("id",online.id()).query(String.class).single()).isEqualTo("canceled");
        var inPerson = sessions.propose(A,new LearningSessionController.Propose(match.id(),A,start,start.plusHours(1),
            SessionMode.IN_PERSON,"  Library  ",null,"  Bring notes  "));
        assertThat(inPerson.meetingUrl()).isNull();
        assertThat(inPerson.location()).isEqualTo("Library");
        assertThat(inPerson.message()).isEqualTo("Bring notes");
        assertThat(sessions.change(B,inPerson.id(),SessionStatus.DECLINED).status()).isEqualTo(SessionStatus.DECLINED);
        assertThat(sessions.list(B,LearningSessionController.Direction.all,match.id(),0,20).total()).isEqualTo(2);
        expect(404,()->sessions.get(new UUID(0,99),online.id()));
    }

    @Test void competingSessionAcceptAndDeclineHaveOneWinnerOnPostgres() throws Exception {
        var match = operations.sendRequest(A,B,1);
        operations.acceptRequest(B,match.id());
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        var session = sessions.propose(A,new LearningSessionController.Propose(match.id(),A,start,start.plusHours(1),
            SessionMode.ONLINE,null,"https://meet.example.test/room",null));
        assertThat(race(()->sessions.change(B,session.id(),SessionStatus.ACCEPTED),
            ()->sessions.change(B,session.id(),SessionStatus.DECLINED))).containsExactlyInAnyOrder(200,409);
        assertThat(sessions.get(A,session.id()).status()).isIn(SessionStatus.ACCEPTED,SessionStatus.DECLINED);
    }

    @Test void postgresEnforcesSessionParentAndStoredModeContract() {
        var match = operations.sendRequest(A,B,1);
        operations.acceptRequest(B,match.id());
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        var session = sessions.propose(A,new LearningSessionController.Propose(match.id(),A,start,start.plusHours(1),
            SessionMode.ONLINE,null,"https://meet.example.test/room",null));
        assertThatThrownBy(()->db.sql("delete from match_requests where request_id=:id").param("id",match.id()).update())
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.sql("update learning_sessions set mode='remote' where session_id=:id").param("id",session.id()).update())
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sessions.get(B,session.id()).mode()).isEqualTo(SessionMode.ONLINE);
    }

    private List<Integer> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = Stream.of(first, second).map(task -> executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { task.call(); return 200; }
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
