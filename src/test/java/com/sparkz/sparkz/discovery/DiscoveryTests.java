package com.sparkz.sparkz.discovery;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DiscoveryTests {
    static final UUID A = id(1), B = id(2), C = id(3), D = id(4);
    @Autowired JdbcClient db;
    @Autowired DiscoveryService service;
    @Autowired MockMvc mvc;
    static UUID id(int n) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(n)); }

    @BeforeEach void seed() {
        db.sql("update profiles set school_name = 'Demo University', onboarding_completed = true, account_status = 'active', email = 'private@example.com', address = 'Private address', phone = 'private', dob = date '2000-01-01'").update();
        db.sql("insert into profiles(user_id, username, school_name, onboarding_completed) values (:c,'Casey','Demo University',true),(:d,'Drew','Demo University',true)").param("c",C).param("d",D).update();
        db.sql("insert into skills(skill_id, skill_name) values (20,'Guitar'), (21,'Piano')").update();
        db.sql("insert into user_skills values (:a,20,'learn'), (:b,20,'teach'), (:c,20,'teach'), (:d,21,'teach')").param("a",A).param("b",B).param("c",C).param("d",D).update();
        db.sql("insert into profile_available_days values (:a,1),(:a,2),(:b,2),(:c,1),(:c,2)").param("a",A).param("b",B).param("c",C).update();
        db.sql("insert into profile_available_slots(user_id,day_of_week,start_time,end_time) values (:b,2,time '10:00:00',time '11:00:00')").param("b",B).update();
    }

    @Test void prioritizesReciprocalThenCommonDaysAndExplainsMatches() {
        var page = service.browse(A,null,null,null,false,0,20);
        assertThat(page.profiles()).extracting(DiscoveryService.Person::userId).containsExactly(B,C,D);
        var first = page.profiles().get(0);
        assertThat(first.reciprocal()).isTrue();
        assertThat(first.canLearn()).extracting(DiscoveryService.Skill::skillName).containsExactly("Guitar");
        assertThat(first.canTeach()).extracting(DiscoveryService.Skill::skillName).containsExactly("Java");
        assertThat(first.commonDays()).containsExactly(2);
        assertThat(first.reasons()).anyMatch(s -> s.contains("learn Guitar")).anyMatch(s -> s.contains("teach Java"));
        assertThat(service.browse(A,null,null,null,true,0,20).profiles()).extracting(DiscoveryService.Person::userId).containsExactly(B,C);
        db.sql("delete from user_skills where user_id=:b and skill_id=1").param("b",B).update();
        assertThat(service.browse(A,null,null,null,true,0,20).profiles()).extracting(DiscoveryService.Person::userId).containsExactly(C,B);
    }

    @Test void skillRoleDayFiltersAndPagination() throws Exception {
        mvc.perform(get("/api/discovery?skillId=20&skillType=teach&day=1").with(user(A.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.profiles[0].userId").value(C.toString()));
        mvc.perform(get("/api/discovery?skillId=20&skillType=learn").with(user(A.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/discovery?offset=1&limit=1").with(user(A.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.profiles.length()").value(1))
            .andExpect(jsonPath("$.profiles[0].userId").value(C.toString()));
        mvc.perform(get("/api/discovery?offset=100").with(user(A.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profiles").isEmpty());
    }

    @Test void privateDataNeverAppearsInPublicProfileOrBrowse() throws Exception {
        for (String path : List.of("/api/discovery", "/api/discovery/"+B)) {
            String body = mvc.perform(get(path).with(user(A.toString()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("private@example.com", "Private address", "startTime", "endTime", "slotId", "10:00", "dob", "phone", "email", "lastName");
        }
        mvc.perform(get("/api/discovery/"+B).with(user(A.toString())))
            .andExpect(jsonPath("$.availableDays[0]").value(2));
    }

    @ParameterizedTest @ValueSource(strings = {"inactive","incomplete","other-school","blocked-by-viewer","blocked-by-candidate"})
    void excludesIneligibleProfilesAndHidesTheirDetails(String reason) throws Exception {
        switch(reason) {
            case "inactive" -> db.sql("update profiles set account_status='deactivated' where user_id=:b").param("b",B).update();
            case "incomplete" -> db.sql("update profiles set onboarding_completed=false where user_id=:b").param("b",B).update();
            case "other-school" -> db.sql("update profiles set school_name='Other University' where user_id=:b").param("b",B).update();
            case "blocked-by-viewer" -> db.sql("insert into blocked_users(blocker_id,blocked_id) values (:a,:b)").param("a",A).param("b",B).update();
            case "blocked-by-candidate" -> db.sql("insert into blocked_users(blocker_id,blocked_id) values (:b,:a)").param("a",A).param("b",B).update();
        }
        assertThat(service.browse(A,null,null,null,false,0,20).profiles()).extracting(DiscoveryService.Person::userId).doesNotContain(A,B);
        mvc.perform(get("/api/discovery/"+B).with(user(A.toString()))).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"account_status='deactivated'", "onboarding_completed=false", "school_name='Unsupported'", "school_name=null"})
    void ineligibleViewersCannotDiscover(String update) throws Exception {
        db.sql("update profiles set "+update+" where user_id=:a").param("a",A).update();
        mvc.perform(get("/api/discovery").with(user(A.toString()))).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"day=0","day=8","skillId=0","skillType=mentor","limit=101","limit=0","offset=-1"})
    void invalidFiltersAreRejected(String query) throws Exception {
        mvc.perform(get("/api/discovery?"+query).with(user(A.toString()))).andExpect(status().isBadRequest());
    }

    @Test void recommendationCanCreateMatchAndReportsPendingStatus() throws Exception {
        var candidate = service.profile(A,B);
        long skill = candidate.canLearn().get(0).skillId();
        mvc.perform(post("/api/match-requests").with(user(A.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"receiverId\":\""+B+"\",\"skillId\":"+skill+"}"))
            .andExpect(status().isCreated());
        assertThat(service.profile(A,B).matchStatus()).isEqualTo("pending");
        mvc.perform(get("/api/discovery")).andExpect(status().isUnauthorized());
    }
}
