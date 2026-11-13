package com.sparkz.sparkz.sessions;

import com.jayway.jsonpath.JsonPath;
import com.sparkz.sparkz.matching.MatchOperations;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearningSessionTests {
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    static final OffsetDateTime START = OffsetDateTime.now(ZoneOffset.ofHours(-6)).plusDays(2).truncatedTo(ChronoUnit.SECONDS);
    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;
    @Autowired MatchOperations matches;
    long matchId;

    @BeforeEach void match() {
        db.sql("insert into profiles(user_id, username, school_name, onboarding_completed) values (:c,'Casey','Demo University',true)").param("c", C).update();
        var match = matches.sendRequest(A, B, 1);
        matches.acceptRequest(B, match.id());
        matchId = match.id();
    }

    @Test void onlineProposalPersistsRolesVerifiedProposerAndUtcTimes() throws Exception {
        String body = body().replace("}", ",\"proposedBy\":\""+C+"\",\"learnerId\":\""+C+"\",\"status\":\"accepted\",\"location\":\"ignored\",\"message\":\"  Bring notes  \"}");
        long id = id(propose(A, body).andExpect(status().isCreated())
            .andExpect(jsonPath("$.proposedBy").value(A.toString()))
            .andExpect(jsonPath("$.teacherId").value(A.toString()))
            .andExpect(jsonPath("$.learnerId").value(B.toString()))
            .andExpect(jsonPath("$.mode").value("online"))
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.location").doesNotExist())
            .andExpect(jsonPath("$.message").value("Bring notes")));
        mvc.perform(get("/api/learning-sessions/"+id).with(user(B.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scheduledStart").value(START.toInstant().toString()));
        assertThat(db.sql("select scheduled_start from learning_sessions where session_id=:id").param("id",id)
            .query((rs,row)->rs.getTimestamp(1).toInstant()).single()).isEqualTo(START.toInstant());
        assertThat(db.sql("select status from learning_sessions where session_id=:id").param("id",id).query(String.class).single()).isEqualTo("pending");
    }

    @Test void learnerCanProposeInPersonWithTheSameTeacher() throws Exception {
        String body = body().replace("\"online\"", "\"in-person\"").replace("}", ",\"location\":\"  Library room 2  \"}");
        propose(B, body).andExpect(status().isCreated())
            .andExpect(jsonPath("$.proposedBy").value(B.toString()))
            .andExpect(jsonPath("$.teacherId").value(A.toString()))
            .andExpect(jsonPath("$.location").value("Library room 2"))
            .andExpect(jsonPath("$.meetingUrl").doesNotExist());
    }

    @ParameterizedTest @ValueSource(strings={"pending","declined","canceled"})
    void onlyAcceptedMatchesCanSchedule(String state) throws Exception {
        db.sql("update match_requests set status=:status where request_id=:id").param("status",state).param("id",matchId).update();
        propose(A, body()).andExpect(status().isConflict());
        assertThat(db.sql("select count(*) from learning_sessions").query(Long.class).single()).isZero();
    }

    @Test void invalidParticipantsAndTeacherRolesAreRejected() throws Exception {
        propose(C, body()).andExpect(status().isNotFound());
        propose(A, body().replace(A.toString(),C.toString())).andExpect(status().isBadRequest());
        propose(A, body().replace(A.toString(),B.toString())).andExpect(status().isUnprocessableEntity());
        propose(A, body().replace("\"matchRequestId\":"+matchId,"\"matchRequestId\":999999")).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings={"inactive","incomplete","other-school","null-school","blocked-by-teacher","blocked-by-learner","skill-removed","same-role","match-canceled"})
    void proposalAndAcceptanceRecheckEligibilityWhileDeclineRemainsAvailable(String reason) throws Exception {
        long id = id(propose(A,body()).andExpect(status().isCreated()));
        switch(reason) {
            case "inactive" -> db.sql("update profiles set account_status='deactivated' where user_id=:b").param("b",B).update();
            case "incomplete" -> db.sql("update profiles set onboarding_completed=false where user_id=:b").param("b",B).update();
            case "other-school" -> db.sql("update profiles set school_name='Other University' where user_id=:b").param("b",B).update();
            case "null-school" -> db.sql("update profiles set school_name=null where user_id=:b").param("b",B).update();
            case "blocked-by-teacher" -> db.sql("insert into blocked_users(blocker_id,blocked_id) values(:a,:b)").param("a",A).param("b",B).update();
            case "blocked-by-learner" -> db.sql("insert into blocked_users(blocker_id,blocked_id) values(:b,:a)").param("a",A).param("b",B).update();
            case "skill-removed" -> db.sql("delete from user_skills where user_id=:b").param("b",B).update();
            case "same-role" -> db.sql("update user_skills set skill_type='teach' where user_id=:b").param("b",B).update();
            case "match-canceled" -> db.sql("update match_requests set status='canceled' where request_id=:id").param("id",matchId).update();
        }
        int expected = reason.equals("match-canceled") ? 409 : 422;
        propose(A,body()).andExpect(status().is(expected));
        change(B,id,"accepted").andExpect(status().is(expected));
        change(B,id,"declined").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("declined"));
    }

    @Test void onlyTheOtherParticipantCanAcceptOrDecline() throws Exception {
        long id = id(propose(A,body()).andExpect(status().isCreated()));
        change(A,id,"accepted").andExpect(status().isForbidden());
        change(A,id,"declined").andExpect(status().isForbidden());
        change(C,id,"accepted").andExpect(status().isNotFound());
        mvc.perform(get("/api/learning-sessions/"+id).with(user(C.toString()))).andExpect(status().isNotFound());
        change(B,id,"accepted").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("accepted"));
        change(B,id,"accepted").andExpect(status().isConflict());
    }

    @Test void proposerMayBeTheLearnerAndRecipientTheTeacher() throws Exception {
        long id = id(propose(B,body()).andExpect(status().isCreated()));
        change(B,id,"accepted").andExpect(status().isForbidden());
        change(A,id,"accepted").andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(ints={1,2})
    void eitherParticipantCanCancelPendingOrAcceptedFutureSessions(int actorId) throws Exception {
        UUID actor = new UUID(0,actorId);
        long pending = id(propose(A,body()).andExpect(status().isCreated()));
        change(actor,pending,"canceled").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("canceled"));
        change(B,pending,"accepted").andExpect(status().isConflict());
        long accepted = id(propose(A,body()).andExpect(status().isCreated()));
        change(B,accepted,"accepted").andExpect(status().isOk());
        change(actor,accepted,"canceled").andExpect(status().isOk());
        change(actor,accepted,"canceled").andExpect(status().isConflict());
    }

    @Test void declinedAndStartedSessionsCannotBeReopenedOrChanged() throws Exception {
        long declined = id(propose(A,body()).andExpect(status().isCreated()));
        change(B,declined,"declined").andExpect(status().isOk());
        change(B,declined,"accepted").andExpect(status().isConflict());
        change(A,declined,"canceled").andExpect(status().isConflict());
        long started = id(propose(A,body()).andExpect(status().isCreated()));
        db.sql("update learning_sessions set scheduled_start=:start,scheduled_end=:end where session_id=:id")
            .param("start",java.sql.Timestamp.from(Instant.now().minusSeconds(7200)))
            .param("end",java.sql.Timestamp.from(Instant.now().minusSeconds(3600))).param("id",started).update();
        for (String state : List.of("accepted","declined","canceled")) change(B,started,state).andExpect(status().isConflict());
        change(B,started,"pending").andExpect(status().isBadRequest());
    }

    @Test void listsArePrivateDirectionalFilteredAndPaginated() throws Exception {
        long outgoing = id(propose(A,body()).andExpect(status().isCreated()));
        id(propose(B,body()).andExpect(status().isCreated()));
        db.sql("insert into user_skills values(:b,1,'teach'),(:c,1,'learn')").param("b",B).param("c",C).update();
        var other = matches.sendRequest(B,C,1);
        matches.acceptRequest(C,other.id());
        long privateId = id(propose(B,body().replace("\"matchRequestId\":"+matchId,"\"matchRequestId\":"+other.id())
            .replace(A.toString(),B.toString())).andExpect(status().isCreated()));
        mvc.perform(get("/api/learning-sessions?limit=1&offset=0").with(user(A.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.sessions.length()").value(1)).andExpect(jsonPath("$.sessions[0].id").value(outgoing));
        mvc.perform(get("/api/learning-sessions?direction=outgoing").with(user(A.toString())))
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.sessions[0].proposedBy").value(A.toString()));
        mvc.perform(get("/api/learning-sessions?direction=incoming").with(user(A.toString())))
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.sessions[0].proposedBy").value(B.toString()));
        mvc.perform(get("/api/learning-sessions?matchRequestId="+other.id()).with(user(A.toString())))
            .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.sessions").isEmpty());
        mvc.perform(get("/api/learning-sessions?matchRequestId="+matchId+"&offset=2").with(user(A.toString())))
            .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.sessions").isEmpty());
        mvc.perform(get("/api/learning-sessions/"+privateId).with(user(A.toString()))).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings={"missing-match","missing-teacher","missing-start","missing-end","missing-mode","bad-mode","past","end-first","equal-times","no-offset","missing-url","unsafe-url","relative-url","url-with-credentials","missing-location","long-message"})
    void invalidSessionDetailsAreRejected(String invalid) throws Exception {
        String body = switch(invalid) {
            case "missing-match" -> body().replace("\"matchRequestId\":"+matchId,"\"matchRequestId\":null");
            case "missing-teacher" -> body().replace("\"teacherId\":\""+A+"\"","\"teacherId\":null");
            case "missing-start" -> body().replace("\"scheduledStart\":\""+START+"\"","\"scheduledStart\":null");
            case "missing-end" -> body().replace("\"scheduledEnd\":\""+START.plusHours(1)+"\"","\"scheduledEnd\":null");
            case "missing-mode" -> body().replace("\"mode\":\"online\"","\"mode\":null");
            case "bad-mode" -> body().replace("\"online\"","\"telepathy\"");
            case "past" -> body().replace(START.toString(),START.minusDays(3).toString());
            case "end-first" -> body().replace(START.plusHours(1).toString(),START.minusMinutes(1).toString());
            case "equal-times" -> body().replace(START.plusHours(1).toString(),START.toString());
            case "no-offset" -> body().replace(START.toString(),START.toLocalDateTime().toString());
            case "missing-url" -> body().replace("https://meet.example.test/sparkz","   ");
            case "unsafe-url" -> body().replace("https://meet.example.test/sparkz","javascript:alert(1)");
            case "relative-url" -> body().replace("https://meet.example.test/sparkz","/meeting");
            case "url-with-credentials" -> body().replace("https://meet.example.test/sparkz","https://user:password@meet.example.test/room");
            case "missing-location" -> body().replace("\"online\"","\"in-person\"");
            case "long-message" -> body().replace("}",",\"message\":\""+"x".repeat(2001)+"\"}");
            default -> throw new AssertionError(invalid);
        };
        propose(A,body).andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"limit=0","limit=101","offset=-1","matchRequestId=0","direction=private"})
    void invalidListParametersAreRejected(String query) throws Exception {
        mvc.perform(get("/api/learning-sessions?"+query).with(user(A.toString()))).andExpect(status().isBadRequest());
    }

    @Test void authenticationCsrfAndInvalidChangesAreEnforced() throws Exception {
        mvc.perform(get("/api/learning-sessions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/learning-sessions").with(csrf()).contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/learning-sessions").with(user(A.toString())).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(get("/api/learning-sessions").with(user("invalid-uuid"))).andExpect(status().isUnauthorized());
        long id = id(propose(A,body()).andExpect(status().isCreated()));
        mvc.perform(patch("/api/learning-sessions/"+id).with(user(B.toString())).contentType("application/json").content("{\"status\":\"accepted\"}"))
            .andExpect(status().isForbidden());
        for(String state : List.of("pending","unknown")) change(B,id,state).andExpect(status().isBadRequest());
        mvc.perform(get("/api/learning-sessions/999999").with(user(A.toString()))).andExpect(status().isNotFound());
    }

    @Test void frontendAssetsArePublicWhileSessionDataRequiresAuthentication() throws Exception {
        for (String path : List.of("/", "/browse.html", "/matches.html", "/sessions.html",
                "/sparkz-shell.js", "/sparkz-theme.css", "/sessions.js", "/sessions.css"))
            mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/api/learning-sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profiles/me").header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    private String body() {
        return "{\"matchRequestId\":"+matchId+",\"teacherId\":\""+A+"\",\"scheduledStart\":\""+START+"\",\"scheduledEnd\":\""+START.plusHours(1)
            +"\",\"mode\":\"online\",\"meetingUrl\":\"https://meet.example.test/sparkz\"}";
    }
    private ResultActions propose(UUID actor, String body) throws Exception {
        return mvc.perform(post("/api/learning-sessions").with(user(actor.toString())).with(csrf()).contentType("application/json").content(body));
    }
    private ResultActions change(UUID actor, long id, String state) throws Exception {
        return mvc.perform(patch("/api/learning-sessions/"+id).with(user(actor.toString())).with(csrf()).contentType("application/json").content("{\"status\":\""+state+"\"}"));
    }
    private long id(ResultActions response) throws Exception {
        return JsonPath.<Number>read(response.andReturn().getResponse().getContentAsString(),"$.id").longValue();
    }
}
