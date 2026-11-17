package com.sparkz.sparkz.sessions;

import com.sparkz.sparkz.matching.*;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningSessionService {
    private final LearningSessionStore store;
    private final MatchStore matches;
    private final JdbcClient db;
    private final Set<String> schools;

    public LearningSessionService(LearningSessionStore store, MatchStore matches, JdbcClient db,
            @Value("${sparkz.matching.supported-schools:}") List<String> schools) {
        this.store = store; this.matches = matches; this.db = db; this.schools = new HashSet<>(schools);
    }
    public record Page(List<LearningSession> sessions, long total, int offset, int limit) {}

    @Transactional
    public LearningSession propose(UUID actor, LearningSessionController.Propose request) {
        MatchRequest match = matches.find(request.matchRequestId()).orElseThrow(() -> missing("Match"));
        if (!actor.equals(match.senderId()) && !actor.equals(match.receiverId())) throw missing("Match");
        UUID teacher = request.teacherId();
        if (!teacher.equals(match.senderId()) && !teacher.equals(match.receiverId()))
            throw error(HttpStatus.BAD_REQUEST, "Teacher must be a participant in this match");
        UUID learner = teacher.equals(match.senderId()) ? match.receiverId() : match.senderId();
        eligible(match, teacher, learner);
        Instant start = request.scheduledStart().toInstant().truncatedTo(ChronoUnit.MICROS);
        Instant end = request.scheduledEnd().toInstant().truncatedTo(ChronoUnit.MICROS);
        if (!start.isAfter(Instant.now())) throw error(HttpStatus.BAD_REQUEST, "Session must start in the future");
        if (!end.isAfter(start)) throw error(HttpStatus.BAD_REQUEST, "Session end must be after its start");
        String location = null, url = null;
        if (request.mode() == SessionMode.ONLINE) {
            url = text(request.meetingUrl());
            if (!validMeetingUrl(url)) throw error(HttpStatus.BAD_REQUEST, "Online sessions require an absolute HTTP or HTTPS meeting URL");
        } else {
            location = text(request.location());
            if (location == null) throw error(HttpStatus.BAD_REQUEST, "In-person sessions require a location");
        }
        return store.insert(match.id(), actor, teacher, learner, start, end, request.mode(),
            location, url, text(request.message()), Instant.now());
    }

    @Transactional(readOnly = true)
    public LearningSession get(UUID actor, long id) {
        LearningSession session = store.find(id).orElseThrow(() -> missing("Session"));
        if (!actor.equals(session.teacherId()) && !actor.equals(session.learnerId())) throw missing("Session");
        return session;
    }

    @Transactional(readOnly = true)
    public Page list(UUID actor, LearningSessionController.Direction direction, Long matchId, int offset, int limit) {
        return store.list(actor, direction, matchId, offset, limit);
    }

    @Transactional
    public LearningSession change(UUID actor, long id, SessionStatus next) {
        LearningSession session = get(actor, id);
        if (next == SessionStatus.PENDING) throw error(HttpStatus.BAD_REQUEST, "Cannot reset a session to pending");
        if (next != SessionStatus.CANCELED && actor.equals(session.proposedBy()))
            throw error(HttpStatus.FORBIDDEN, "Only the other participant can accept or decline a proposal");
        boolean eligibleState = session.status() == SessionStatus.PENDING
            || (next == SessionStatus.CANCELED && session.status() == SessionStatus.ACCEPTED);
        if (!eligibleState) throw error(HttpStatus.CONFLICT, "Session cannot change from its current status");
        if (next == SessionStatus.ACCEPTED) {
            MatchRequest match = matches.find(session.matchRequestId()).orElseThrow(() -> missing("Match"));
            eligible(match, session.teacherId(), session.learnerId());
        }
        Instant now = Instant.now();
        if (!session.scheduledStart().isAfter(now))
            throw error(HttpStatus.CONFLICT, "Session has already started");
        if (store.transition(id, session.status(), next, now) != 1)
            throw error(HttpStatus.CONFLICT, "Session changed or has already started; refresh and try again");
        return store.find(id).orElseThrow();
    }

    private void eligible(MatchRequest match, UUID teacher, UUID learner) {
        // Use the matching feature's stable profile-lock order before locking the match.
        List<MatchStore.Profile> profiles = matches.lockProfiles(match.senderId(), match.receiverId());
        String status = db.sql("select status from match_requests where request_id = :id for update")
            .param("id", match.id()).query(String.class).optional().orElse(null);
        if (!"accepted".equals(status)) throw error(HttpStatus.CONFLICT, "Learning sessions require an accepted match");
        if (profiles.size() != 2) throw missing("Profile");
        for (MatchStore.Profile profile : profiles) {
            if (!"active".equals(profile.accountStatus()) || !profile.onboarded())
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Both accounts must be active and onboarded");
        }
        String school = profiles.get(0).school();
        if (school == null || !schools.contains(school) || !school.equals(profiles.get(1).school()))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Both users must attend the same supported school");
        if (matches.blocked(teacher, learner)) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "These users cannot schedule together");
        long compatible = db.sql("""
            select count(*) from user_skills t join user_skills l on t.skill_id = l.skill_id
            where t.user_id = :teacher and l.user_id = :learner and t.skill_id = :skill
              and t.skill_type = 'teach' and l.skill_type = 'learn'
            """).param("teacher", teacher).param("learner", learner).param("skill", match.skillId()).query(Long.class).single();
        if (compatible == 0) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Teacher and learner must have compatible roles for the matched skill");
    }

    private static String text(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static boolean validMeetingUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
    private static ResponseStatusException missing(String type) { return error(HttpStatus.NOT_FOUND, type + " not found"); }
    private static ResponseStatusException error(HttpStatus status, String detail) { return new ResponseStatusException(status, detail); }
}
