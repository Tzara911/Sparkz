package com.sparkz.sparkz.matching;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MatchStore {
    private final JdbcClient db;
    public MatchStore(JdbcClient db) { this.db = db; }

    public record Profile(UUID userId, String accountStatus, boolean onboarded, String school) {}

    // Lock in a stable order, including opposite-direction sends. Locks live until transaction commit.
    public List<Profile> lockProfiles(UUID a, UUID b) {
        return db.sql("""
            select user_id, account_status, onboarding_completed, school_name from profiles
            where user_id in (:a, :b) order by user_id for update
            """).param("a", a).param("b", b).query((rs, row) -> new Profile(
                rs.getObject("user_id", UUID.class), rs.getString("account_status"),
                rs.getBoolean("onboarding_completed"), rs.getString("school_name"))).list();
    }

    public boolean compatible(UUID a, UUID b, long skill) {
        return db.sql("""
            select count(*) from user_skills a join user_skills b on a.skill_id = b.skill_id
            where a.user_id = :a and b.user_id = :b and a.skill_id = :skill
            and ((a.skill_type = 'teach' and b.skill_type = 'learn')
              or (a.skill_type = 'learn' and b.skill_type = 'teach'))
            """).param("a", a).param("b", b).param("skill", skill).query(Long.class).single() > 0;
    }

    public boolean blocked(UUID a, UUID b) {
        return db.sql("""
            select count(*) from blocked_users where
            (blocker_id = :a and blocked_id = :b) or (blocker_id = :b and blocked_id = :a)
            """).param("a", a).param("b", b).query(Long.class).single() > 0;
    }

    public boolean hasActive(UUID a, UUID b) {
        return db.sql("""
            select count(*) from match_requests
            where ((sender_id = :a and receiver_id = :b) or (sender_id = :b and receiver_id = :a))
              and status in ('pending', 'accept')
            """).param("a", a).param("b", b).query(Long.class).single() > 0;
    }

    public Optional<MatchRequest> find(long id) {
        return db.sql("select * from match_requests where request_id = :id").param("id", id)
            .query(MatchStore::map).optional();
    }

    public List<MatchRequest> list(UUID user, boolean incoming) {
        return db.sql("select * from match_requests where " + (incoming ? "receiver_id" : "sender_id")
                + " = :user order by created_at desc, request_id")
            .param("user", user).query(MatchStore::map).list();
    }

    public MatchRequest insert(UUID sender, UUID receiver, long skill, java.time.Instant now) {
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.sql("""
            insert into match_requests(sender_id, receiver_id, skill_id, status, created_at, status_updated_at)
            values (:sender, :receiver, :skill, 'pending', :now, :now)
            """).param("sender", sender).param("receiver", receiver).param("skill", skill)
            .param("now", java.sql.Timestamp.from(now)).update(keys, "request_id");
        return new MatchRequest(java.util.Objects.requireNonNull(keys.getKey()).longValue(),
            sender, receiver, skill, MatchStatus.Pending, now, now);
    }

    public int transition(long id, MatchStatus status, java.time.Instant now) {
        return db.sql("""
            update match_requests set status = :status, status_updated_at = :now
            where request_id = :id and status = 'pending'
            """).param("id", id).param("status", status.databaseValue())
            .param("now", java.sql.Timestamp.from(now)).update();
    }

    private static MatchRequest map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new MatchRequest(rs.getLong("request_id"), rs.getObject("sender_id", UUID.class),
            rs.getObject("receiver_id", UUID.class), rs.getLong("skill_id"), MatchStatus.fromDatabaseValue(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("status_updated_at").toInstant());
    }
}
