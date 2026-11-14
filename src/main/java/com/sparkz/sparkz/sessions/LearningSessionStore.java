package com.sparkz.sparkz.sessions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LearningSessionStore {
    private final JdbcClient db;
    public LearningSessionStore(JdbcClient db) { this.db = db; }

    public Optional<LearningSession> find(long id) {
        return db.sql("select * from learning_sessions where session_id = :id")
            .param("id", id).query(LearningSessionStore::map).optional();
    }

    public LearningSession insert(long matchId, UUID actor, UUID teacher, UUID learner,
            Instant start, Instant end, SessionMode mode, String location,
            String meetingUrl, String message, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.sql("""
            insert into learning_sessions(match_request_id, proposed_by, teacher_id, learner_id,
                scheduled_start, scheduled_end, mode, location, meeting_url, status, created_at, message)
            values (:match, :actor, :teacher, :learner, :start, :end, :mode, :location, :url, 'pending', :now, :message)
            """).param("match", matchId).param("actor", actor).param("teacher", teacher).param("learner", learner)
            .param("start", Timestamp.from(start)).param("end", Timestamp.from(end)).param("mode", mode.value())
            .param("location", location, Types.VARCHAR).param("url", meetingUrl, Types.VARCHAR)
            .param("now", Timestamp.from(now)).param("message", message, Types.VARCHAR).update(keys, "session_id");
        return find(Objects.requireNonNull(keys.getKey()).longValue()).orElseThrow();
    }

    public LearningSessionService.Page list(UUID user, LearningSessionController.Direction direction,
            Long matchId, int offset, int limit) {
        String where = " where (teacher_id = :user or learner_id = :user)";
        if (direction == LearningSessionController.Direction.incoming) where += " and proposed_by <> :user";
        if (direction == LearningSessionController.Direction.outgoing) where += " and proposed_by = :user";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", user);
        if (matchId != null) { where += " and match_request_id = :match"; parameters.put("match", matchId); }
        long total = db.sql("select count(*) from learning_sessions" + where).params(parameters).query(Long.class).single();
        List<LearningSession> sessions = db.sql("select * from learning_sessions" + where
                + " order by scheduled_start, session_id limit :limit offset :offset")
            .params(parameters).param("limit", limit).param("offset", offset).query(LearningSessionStore::map).list();
        return new LearningSessionService.Page(sessions, total, offset, limit);
    }

    public int transition(long id, SessionStatus expected, SessionStatus next, Instant now) {
        return db.sql("""
            update learning_sessions set status = :next
            where session_id = :id and status = :expected and scheduled_start > :now
            """).param("id", id).param("expected", expected.value()).param("next", next.value())
            .param("now", Timestamp.from(now)).update();
    }

    private static LearningSession map(ResultSet rs, int row) throws SQLException {
        return new LearningSession(rs.getLong("session_id"), rs.getLong("match_request_id"),
            rs.getObject("proposed_by", UUID.class), rs.getObject("teacher_id", UUID.class),
            rs.getObject("learner_id", UUID.class), rs.getTimestamp("scheduled_start").toInstant(),
            rs.getTimestamp("scheduled_end").toInstant(), SessionMode.from(rs.getString("mode")),
            rs.getString("location"), rs.getString("meeting_url"), SessionStatus.from(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(), rs.getString("message"));
    }
}
