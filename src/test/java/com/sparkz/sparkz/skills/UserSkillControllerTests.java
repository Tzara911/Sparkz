package com.sparkz.sparkz.skills;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparkz.sparkz.profiles.LocalLoginConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms a user can view, add, and remove their own teach/learn skills,
 * and only their own - same self-only-by-construction pattern as
 * ProfileControllerTests. @Transactional for the same reason as there: roll
 * back writes so tests don't permanently change the shared demo data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserSkillControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsTheAuthenticatedUsersOwnSkills() throws Exception {
        // Seeded in data.sql: demo_learner (user 2) learns Guitar (1) and Python (3).
        mockMvc
            .perform(get("/api/profiles/me/skills").with(httpBasic(LocalLoginConfig.DEMO_USER_2, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.skillName == 'Guitar' && @.skillType == 'learn')]").exists())
            .andExpect(jsonPath("$[?(@.skillName == 'Python' && @.skillType == 'learn')]").exists());
    }

    @Test
    void addingASkillShowsUpInTheOwnersListButNotAnotherUsers() throws Exception {
        // Chess is skill id 4. Demo user 1 (the teacher) adds it as something they teach.
        mockMvc
            .perform(put("/api/profiles/me/skills/4/teach").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isNoContent());

        mockMvc
            .perform(get("/api/profiles/me/skills").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.skillName == 'Chess' && @.skillType == 'teach')]").exists());

        // Demo user 2 never asked for Chess, so it shouldn't appear for them.
        mockMvc
            .perform(get("/api/profiles/me/skills").with(httpBasic(LocalLoginConfig.DEMO_USER_2, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.skillName == 'Chess')]").doesNotExist());
    }

    @Test
    void addingTheSameSkillTwiceIsHarmless() throws Exception {
        mockMvc
            .perform(put("/api/profiles/me/skills/4/teach").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isNoContent());
        mockMvc
            .perform(put("/api/profiles/me/skills/4/teach").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isNoContent());

        mockMvc
            .perform(get("/api/profiles/me/skills").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)); // original Guitar/teach + the new Chess/teach, not duplicated
    }

    @Test
    void rejectsAnInvalidSkillType() throws Exception {
        mockMvc
            .perform(put("/api/profiles/me/skills/4/mentor").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASkillIdThatDoesNotExist() throws Exception {
        mockMvc
            .perform(put("/api/profiles/me/skills/999/teach").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isNotFound());
    }

    @Test
    void removingASkillTakesItOffTheOwnersList() throws Exception {
        mockMvc
            .perform(delete("/api/profiles/me/skills/1/teach").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isNoContent());

        mockMvc
            .perform(get("/api/profiles/me/skills").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removingASkillThatIsNotThereIsStillSuccessful() throws Exception {
        mockMvc
            .perform(delete("/api/profiles/me/skills/5/learn").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isNoContent());
    }
}
