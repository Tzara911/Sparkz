package com.sparkz.sparkz.discovery;

import com.sparkz.sparkz.matching.MatchStore;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DiscoveryStore {
    private final JdbcClient db;
    public DiscoveryStore(JdbcClient db) { this.db = db; }
    public record PublicRow(UUID id, String username, String bio, String school) {}
    public record UserSkill(UUID userId, String type, DiscoveryProfile.Skill skill) {}
    public record UserDay(UUID userId, int day) {}

    public Optional<MatchStore.Profile> viewer(UUID id) {
        return db.sql("select user_id, account_status, onboarding_completed, school_name from profiles where user_id = :id")
            .param("id", id).query((rs, row) -> new MatchStore.Profile(rs.getObject("user_id", UUID.class),
                rs.getString("account_status"), rs.getBoolean("onboarding_completed"), rs.getString("school_name"))).optional();
    }

    public List<PublicRow> candidates(UUID viewer, String school, boolean recommended, Long teaches, Long learns,
                                     Integer day, int offset, int limit) {
        String sql = """
            select user_id, username, bio, school_name from (
                select p.user_id, p.username, p.bio, p.school_name,
                    (select count(*) from user_skills mine join user_skills theirs on mine.skill_id = theirs.skill_id
                     where mine.user_id = :viewer and theirs.user_id = p.user_id
                       and mine.skill_type = 'learn' and theirs.skill_type = 'teach') as learn_matches,
                    (select count(*) from user_skills mine join user_skills theirs on mine.skill_id = theirs.skill_id
                     where mine.user_id = :viewer and theirs.user_id = p.user_id
                       and mine.skill_type = 'teach' and theirs.skill_type = 'learn') as teach_matches,
                    (select count(*) from profile_available_days mine join profile_available_days theirs
                     on mine.day_of_week = theirs.day_of_week
                     where mine.user_id = :viewer and theirs.user_id = p.user_id) as common_days
                from profiles p
                where p.user_id <> :viewer and p.account_status = 'active' and p.onboarding_completed = true
                  and p.school_name = :school
                  and not exists (select 1 from blocked_users b where
                      (b.blocker_id = :viewer and b.blocked_id = p.user_id)
                      or (b.blocker_id = p.user_id and b.blocked_id = :viewer))
                  and not exists (select 1 from match_requests m where m.status in ('pending', 'accepted')
                      and ((m.sender_id = :viewer and m.receiver_id = p.user_id)
                        or (m.receiver_id = :viewer and m.sender_id = p.user_id)))
            """;
        if (teaches != null) sql += " and exists (select 1 from user_skills f where f.user_id = p.user_id and f.skill_type = 'teach' and f.skill_id = :teaches)";
        if (learns != null) sql += " and exists (select 1 from user_skills f where f.user_id = p.user_id and f.skill_type = 'learn' and f.skill_id = :learns)";
        if (day != null) sql += " and exists (select 1 from profile_available_days d where d.user_id = p.user_id and d.day_of_week = :day)";
        sql += """
            ) ranked where (:recommended = false or learn_matches + teach_matches > 0)
            order by case when learn_matches > 0 and teach_matches > 0 then 0 else 1 end,
                     (learn_matches + teach_matches) desc, common_days desc,
                     lower(coalesce(username, '')), user_id
            limit :limit offset :offset
            """;
        var query = db.sql(sql).param("viewer", viewer).param("school", school).param("recommended", recommended)
            .param("limit", limit).param("offset", offset);
        if (teaches != null) query.param("teaches", teaches);
        if (learns != null) query.param("learns", learns);
        if (day != null) query.param("day", day);
        return query.query((rs, row) -> new PublicRow(rs.getObject("user_id", UUID.class), rs.getString("username"),
            rs.getString("bio"), rs.getString("school_name"))).list();
    }

    public List<UserSkill> skills(Collection<UUID> users) {
        return db.sql("""
            select us.user_id, us.skill_type, s.skill_id, s.skill_name, s.description
            from user_skills us join skills s on s.skill_id = us.skill_id
            where us.user_id in (:users) order by lower(s.skill_name), s.skill_id
            """).param("users", users).query((rs, row) -> new UserSkill(rs.getObject("user_id", UUID.class),
                rs.getString("skill_type"), new DiscoveryProfile.Skill(rs.getLong("skill_id"), rs.getString("skill_name"), rs.getString("description")))).list();
    }
    public List<UserDay> days(Collection<UUID> users) {
        return db.sql("select user_id, day_of_week from profile_available_days where user_id in (:users) order by day_of_week")
            .param("users", users).query((rs, row) -> new UserDay(rs.getObject("user_id", UUID.class), rs.getInt("day_of_week"))).list();
    }
    public List<DiscoveryProfile.Skill> catalog() {
        return db.sql("select skill_id, skill_name, description from skills order by lower(skill_name), skill_id")
            .query((rs, row) -> new DiscoveryProfile.Skill(rs.getLong("skill_id"), rs.getString("skill_name"), rs.getString("description"))).list();
    }
}
