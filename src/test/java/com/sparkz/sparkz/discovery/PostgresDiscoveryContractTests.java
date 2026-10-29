package com.sparkz.sparkz.discovery;

import com.sparkz.sparkz.matching.MatchOperations;
import com.sparkz.sparkz.matching.MatchStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Shares the disposable PostgreSQL fixture/context with PostgresMatchContractTests. */
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
class PostgresDiscoveryContractTests {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    @Autowired DiscoveryService discovery;
    @Autowired MatchOperations matches;
    @Autowired JdbcClient db;

    @BeforeEach void reset() {
        db.sql("delete from match_requests").update();
        db.sql("delete from blocked_users").update();
        db.sql("delete from user_skills").update();
        db.sql("delete from profile_available_days").update();
        db.sql("delete from skills where skill_id <> 1").update();
        db.sql("insert into user_skills values (:a, 1, 'teach'), (:b, 1, 'learn')")
            .param("a", A).param("b", B).update();
        db.sql("insert into profile_available_days values (:a, 1), (:b, 1)")
            .param("a", A).param("b", B).update();
    }

    @Test void readsUuidRolesDaysAndNullableSkillDescriptions() {
        var profile = discovery.discover(A, true, null, 1L, 1, 0, 12).profiles().get(0);
        assertThat(profile.userId()).isEqualTo(B);
        assertThat(profile.commonDays()).containsExactly(1);
        assertThat(profile.learningSkills().get(0).description()).isEqualTo("Java programming");
        assertThat(discovery.catalog(A).get(0).description()).isEqualTo("Java programming");
        db.sql("update skills set description = null where skill_id = 1").update();
        assertThat(discovery.catalog(A).get(0).description()).isNull();
        db.sql("update skills set description = 'Java programming' where skill_id = 1").update();
    }

    @Test void acceptedDatabaseRowsAndNewAcceptancesDisappearInBothDirections() {
        db.sql("insert into match_requests(sender_id, receiver_id, skill_id, status) values (:b, :a, 1, 'accepted')")
            .param("a", A).param("b", B).update();
        assertHiddenBothWays();
        assertThat(matches.list(A, true).get(0).status()).isEqualTo(MatchStatus.Accepted);
        db.sql("delete from match_requests").update();
        assertThat(discovery.discover(A, true, null, null, null, 0, 12).profiles()).hasSize(1);
        var request = matches.sendRequest(A, B, 1);
        matches.acceptRequest(B, request.id());
        assertHiddenBothWays();
    }

    @Test void discoveryAndMatchingPreserveBigintSkillIds() {
        long skill = 2147483648L;
        db.sql("insert into skills(skill_id, skill_name) overriding system value values (:id, 'Large skill')")
            .param("id", skill).update();
        db.sql("insert into user_skills values (:a, :id, 'learn'), (:b, :id, 'teach')")
            .param("a", A).param("b", B).param("id", skill).update();
        var candidate = discovery.discover(A, true, skill, null, null, 0, 12).profiles().get(0);
        assertThat(candidate.learnFromThem().get(0).id()).isEqualTo(skill);
        assertThat(matches.sendRequest(A, B, skill).skillId()).isEqualTo(skill);
    }

    private void assertHiddenBothWays() {
        assertThat(discovery.discover(A, true, null, null, null, 0, 12).profiles()).isEmpty();
        assertThat(discovery.discover(B, false, null, null, null, 0, 12).profiles()).isEmpty();
    }
}
