package com.sparkz.sparkz.matching;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

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
class PostgresMatchContractTests {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    @Autowired MatchOperations operations;
    @Autowired JdbcClient db;

    @BeforeEach void reset() {
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

    private List<Integer> race(Callable<MatchRequest> first, Callable<MatchRequest> second) throws Exception {
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
