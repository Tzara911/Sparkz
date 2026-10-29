// Sparkz Group
// This file lets the application read the skills catalog in the database.

package com.sparkz.sparkz.skills;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;


@Repository
public class SkillRepository {

    private final JdbcClient jdbcClient;

    public SkillRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Skill> findAll() {
        return jdbcClient
            .sql("SELECT skill_id, skill_name, description FROM skills ORDER BY skill_name")
            .query(Skill.class)
            .list();
    }

    public boolean existsById(Long skillId) {
        Integer count =
            jdbcClient
                .sql("SELECT COUNT(*) FROM skills WHERE skill_id = :skillId")
                .param("skillId", skillId)
                .query(Integer.class)
                .single();
        return count > 0;
    }
}
