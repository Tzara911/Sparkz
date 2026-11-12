// Sparkz Group
// This file lets the application read and write a user's own skills in the database.

package com.sparkz.sparkz.skills;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;


@Repository
public class UserSkillRepository {

    private final JdbcClient jdbcClient;

    public UserSkillRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<UserSkill> findByUserId(UUID userId) {
        return jdbcClient
            .sql(
                """
                SELECT s.skill_id, s.skill_name, us.skill_type
                FROM user_skills us
                JOIN skills s ON s.skill_id = us.skill_id
                WHERE us.user_id = :userId
                ORDER BY s.skill_name
                """)
            .param("userId", userId)
            .query(UserSkill.class)
            .list();
    }

    /**
     * Adds a user/skill/type association. The composite primary key and atomic
     * conflict handling keep repeated and concurrent adds idempotent on
     * PostgreSQL and H2's PostgreSQL mode.
     */
    public void add(UUID userId, Long skillId, String skillType) {
        jdbcClient
            .sql(
                """
                INSERT INTO user_skills (user_id, skill_id, skill_type)
                VALUES (:userId, :skillId, :skillType)
                ON CONFLICT DO NOTHING
                """)
            .param("userId", userId)
            .param("skillId", skillId)
            .param("skillType", skillType)
            .update();
    }

    /** Removes a skill/type pairing for a user. Returns how many rows were deleted (0 if it wasn't there). */
    public int remove(UUID userId, Long skillId, String skillType) {
        return jdbcClient
            .sql("DELETE FROM user_skills WHERE user_id = :userId AND skill_id = :skillId AND skill_type = :skillType")
            .param("userId", userId)
            .param("skillId", skillId)
            .param("skillType", skillType)
            .update();
    }
}
