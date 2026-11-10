package com.sparkz.sparkz.skills;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparkz.sparkz.profiles.LocalLoginConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Confirms GET /api/skills returns the full skills catalog seeded in data.sql. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/skills")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheFullSkillsCatalog() throws Exception {
        mockMvc
            .perform(get("/api/skills").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[?(@.skillName == 'Guitar')]").exists())
            .andExpect(jsonPath("$[?(@.skillName == 'Python')]").exists());
    }
}
