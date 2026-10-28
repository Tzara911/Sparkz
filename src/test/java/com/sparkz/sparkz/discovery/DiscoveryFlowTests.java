package com.sparkz.sparkz.discovery;

import com.sparkz.sparkz.matching.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:discovery-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscoveryFlowTests {
    static final UUID A = id(1), B = id(2), C = id(3), D = id(4), E = id(5);
    @Autowired DiscoveryService discovery;
    @Autowired MatchService matches;
    @Autowired JdbcClient db;
    @Autowired MockMvc mvc;

    @BeforeEach void seed() {
        db.sql("delete from match_requests").update();
        db.sql("delete from blocked_users").update();
        db.sql("delete from user_skills").update();
        db.sql("delete from profile_available_days").update();
        db.sql("delete from profiles").update();
        db.sql("delete from skills").update();
        db.sql("insert into skills(skill_id, skill_name) values (1, 'Java'), (2, 'Spanish'), (3, 'Painting')").update();
        profile(A, "Viewer"); profile(B, "Sam"); profile(C, "Charlie"); profile(D, "Drew"); profile(E, "Ellis");
        skill(A, 1, "teach"); skill(A, 2, "learn"); days(A, 1, 3);
        skill(B, 1, "learn"); skill(B, 2, "teach"); days(B, 1);
        skill(C, 1, "learn"); days(C, 1, 3);
        skill(D, 1, "learn"); skill(D, 2, "teach"); days(D, 1, 3);
        skill(E, 3, "teach"); days(E, 7);
    }

    @Test void ranksReciprocalThenCommonDaysAndExplainsBothDirections() {
        var result = recommended();
        assertThat(result.profiles()).extracting(DiscoveryProfile::userId).containsExactly(D, B, C);
        var first = result.profiles().get(0);
        assertThat(first.reciprocal()).isTrue();
        assertThat(first.learnFromThem()).extracting(DiscoveryProfile.Skill::name).containsExactly("Spanish");
        assertThat(first.teachThem()).extracting(DiscoveryProfile.Skill::name).containsExactly("Java");
        assertThat(first.commonDays()).containsExactly(1, 3);
        assertThat(result.profiles().get(2).reciprocal()).isFalse();
    }

    @Test void skillCountRanksBeforeAvailabilityWithinTheSameReciprocityGroup() {
        skill(A, 3, "learn"); skill(B, 3, "teach");
        assertThat(ids(recommended())).containsExactly(B, D, C, E);
    }

    @Test void browseIncludesPeopleWithoutComplementarySkillsAndMissingOptionalFields() {
        db.sql("update profiles set username = null, bio = null where user_id = :id").param("id", E).update();
        var all = discovery.discover(A, false, null, null, null, 0, 12);
        assertThat(ids(all)).containsExactly(D, B, C, E);
        var last = all.profiles().get(3);
        assertThat(last.username()).isNull();
        assertThat(last.learnFromThem()).isEmpty();
        assertThat(last.teachThem()).isEmpty();
    }

    @Test void filtersTeachingLearningAndPublicWeekdaysTogether() throws Exception {
        mvc.perform(get("/api/discovery?teaches=2&learns=1&day=3").with(user(A.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profiles.length()").value(1))
            .andExpect(jsonPath("$.profiles[0].userId").value(D.toString()));
        assertThat(ids(discovery.discover(A, false, 3L, null, 7, 0, 12))).containsExactly(E);
        assertThat(ids(discovery.discover(A, true, null, 1L, null, 0, 12))).containsExactly(D, B, C);
        assertThat(discovery.discover(A, true, 999L, null, null, 0, 12).profiles()).isEmpty();
    }

    @Test void stablePaginationDoesNotLoseOrRepeatTiedProfiles() {
        days(B, 3); // B and D now tie on skill and day counts; names break the tie.
        var first = discovery.discover(A, true, null, null, null, 0, 1);
        var second = discovery.discover(A, true, null, null, null, 1, 1);
        var third = discovery.discover(A, true, null, null, null, 2, 1);
        assertThat(ids(first)).containsExactly(D);
        assertThat(ids(second)).containsExactly(B);
        assertThat(ids(third)).containsExactly(C);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isTrue();
        assertThat(third.hasNext()).isFalse();
        assertThat(discovery.discover(A, true, null, null, null, 3, 1).profiles()).isEmpty();
    }

    @Test void excludesSelfInactiveIncompleteOtherSchoolsAndBothBlockDirections() {
        for (int i = 6; i <= 10; i++) { profile(id(i), "Hidden" + i); skill(id(i), 1, "learn"); }
        db.sql("update profiles set account_status = 'deactivated' where user_id = :id").param("id", id(6)).update();
        db.sql("update profiles set onboarding_completed = false where user_id = :id").param("id", id(7)).update();
        db.sql("update profiles set school_name = 'Other University' where user_id = :id").param("id", id(8)).update();
        db.sql("insert into blocked_users(blocker_id, blocked_id) values (:a, :b), (:c, :a)")
            .param("a", A).param("b", id(9)).param("c", id(10)).update();
        assertThat(ids(recommended())).containsExactly(D, B, C);
        assertThat(ids(discovery.discover(A, false, null, null, null, 0, 12))).containsExactly(D, B, C, E);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "accept"})
    void excludesExistingActivePairsInEitherDirectionAndAcrossSkills(String status) {
        db.sql("insert into match_requests(sender_id, receiver_id, skill_id, status) values (:a, :b, 1, :status), (:d, :a, 2, :status)")
            .param("a", A).param("b", B).param("d", D).param("status", status).update();
        assertThat(ids(recommended())).containsExactly(C);
        assertThat(ids(discovery.discover(A, false, null, null, null, 0, 12))).containsExactly(C, E);
    }

    @Test void discoveryToRequestToCancelDeclineAndAcceptUsesIssueEightStateFlow() throws Exception {
        var suggested = recommended().profiles().get(0);
        mvc.perform(post("/api/match-requests").with(user(A.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + suggested.userId() + "\",\"skillId\":\"" + suggested.learnFromThem().get(0).id() + "\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("Pending"));
        var request = matches.list(D, true).get(0);
        assertThat(ids(recommended())).doesNotContain(D);
        assertThat(ids(discovery.discover(D, true, null, null, null, 0, 12))).doesNotContain(A);
        matches.change(A, request.id(), MatchStatus.Canceled);
        assertThat(ids(recommended())).contains(D);
        request = matches.send(A, D, 1);
        matches.change(D, request.id(), MatchStatus.Declined);
        assertThat(ids(recommended())).contains(D);
        request = matches.send(D, A, 2);
        matches.change(A, request.id(), MatchStatus.Accepted);
        assertThat(ids(recommended())).doesNotContain(D);
    }

    @Test void staleRecommendationCannotBypassSendEligibility() throws Exception {
        assertThat(ids(recommended())).contains(B);
        db.sql("insert into blocked_users(blocker_id, blocked_id) values (:a, :b)").param("a", B).param("b", A).update();
        mvc.perform(post("/api/match-requests").with(user(A.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiverId\":\"" + B + "\",\"skillId\":1}"))
            .andExpect(status().isUnprocessableEntity());
        assertThat(matches.list(A, false)).isEmpty();
    }

    @Test void responseContainsOnlyPublicProjectionAndCatalogUsesSharedIds() throws Exception {
        mvc.perform(get("/api/discovery").with(user(A.toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profiles[0].*", hasSize(11)))
            .andExpect(jsonPath("$.profiles[0].bio").value("Public bio"))
            .andExpect(jsonPath("$.profiles[0].availableDays", contains(1, 3)))
            .andExpect(jsonPath("$.profiles[0].email").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].address").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].phone").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].dob").doesNotExist())
            .andExpect(jsonPath("$.profiles[0].availableSlots").doesNotExist())
            .andExpect(content().string(not(containsString("secret@example.test"))));
        mvc.perform(get("/api/discovery/skills").with(user(A.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("Java"));
        // The fixture has no private slots table: discovery must work without reading it.
    }

    @ParameterizedTest
    @ValueSource(strings = {"day=0", "day=8", "teaches=0", "learns=-1", "page=-1", "page=10001", "size=0", "size=51", "mode=unknown"})
    void rejectsInvalidFilters(String query) throws Exception {
        mvc.perform(get("/api/discovery?" + query).with(user(A.toString()))).andExpect(status().isBadRequest());
    }

    @Test void requiresAuthenticationAndEligibleViewerForBothEndpoints() throws Exception {
        for (String endpoint : List.of("/api/discovery", "/api/discovery/skills")) {
            mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
            mvc.perform(get(endpoint).with(user(id(99).toString()))).andExpect(status().isNotFound());
        }
        for (String update : List.of("account_status = 'deactivated'", "onboarding_completed = false", "school_name = 'Unsupported'", "school_name = null")) {
            db.sql("update profiles set account_status = 'active', onboarding_completed = true, school_name = 'Demo University' where user_id = :id").param("id", A).update();
            db.sql("update profiles set " + update + " where user_id = :id").param("id", A).update();
            for (String endpoint : List.of("/api/discovery", "/api/discovery/skills"))
                mvc.perform(get(endpoint).with(user(A.toString()))).andExpect(status().isUnprocessableEntity());
        }
    }

    @Test void missingAvailabilityDoesNotExcludeOtherwiseCompatiblePeople() {
        db.sql("delete from profile_available_days").update();
        assertThat(ids(recommended())).containsExactly(D, B, C);
        assertThat(recommended().profiles()).allSatisfy(p -> {
            assertThat(p.availableDays()).isEmpty(); assertThat(p.commonDays()).isEmpty();
        });
    }

    private DiscoveryProfile.Page recommended() { return discovery.discover(A, true, null, null, null, 0, 12); }
    private List<UUID> ids(DiscoveryProfile.Page page) { return page.profiles().stream().map(DiscoveryProfile::userId).toList(); }
    private static UUID id(int n) { return new UUID(0, n); }
    private void profile(UUID id, String name) {
        db.sql("insert into profiles(user_id, username, bio, email, onboarding_completed, school_name) values (:id, :name, 'Public bio', 'secret@example.test', true, 'Demo University')")
            .param("id", id).param("name", name).update();
    }
    private void skill(UUID id, long skill, String type) {
        db.sql("insert into user_skills values (:id, :skill, :type)").param("id", id).param("skill", skill).param("type", type).update();
    }
    private void days(UUID id, int... days) {
        for (int day : days) db.sql("insert into profile_available_days values (:id, :day)").param("id", id).param("day", day).update();
    }
}
