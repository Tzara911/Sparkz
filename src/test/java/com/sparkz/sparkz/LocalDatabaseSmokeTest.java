package com.sparkz.sparkz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Confirms the local/test H2 database plumbing actually works: schema.sql creates the
 * tables and data.sql seeds them. This is infrastructure-only - no profile/skills
 * business logic is tested here, just that the database is usable.
 */
@SpringBootTest
@ActiveProfiles("test")
class LocalDatabaseSmokeTest {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void schemaAndSeedDataLoad() {
        Integer profileCount = jdbcClient.sql("SELECT COUNT(*) FROM profiles").query(Integer.class).single();
        Integer skillCount = jdbcClient.sql("SELECT COUNT(*) FROM skills").query(Integer.class).single();
        Integer userSkillCount = jdbcClient.sql("SELECT COUNT(*) FROM user_skills").query(Integer.class).single();

        assertThat(profileCount).isEqualTo(2);
        assertThat(skillCount).isEqualTo(5);
        assertThat(userSkillCount).isEqualTo(3);
    }
}
