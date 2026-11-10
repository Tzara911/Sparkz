package com.sparkz.sparkz.skills;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Loads the real application and both skill implementations without demo login overrides. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:skill-catalog-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/local-schema.sql",
    "spring.sql.init.data-locations=classpath:db/local-data.sql",
    "spring.jpa.hibernate.ddl-auto=none",
    "sparkz.auth.issuer=https://example.invalid/auth/v1"
})
@AutoConfigureMockMvc
@ActiveProfiles("catalog-startup")
class SkillCatalogStartupTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void catalogRemainsAvailable() throws Exception {
        mockMvc.perform(get("/api/skills").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].skillId").value(1))
            .andExpect(jsonPath("$[0].skillName").value("Java"));
    }

    @Test
    void detailRemainsAvailable() throws Exception {
        mockMvc.perform(get("/api/skills/1").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skillId").value(1))
            .andExpect(jsonPath("$.skillName").value("Java"));
    }

    @Test
    void missingDetailReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/skills/999").with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void bothRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/skills")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/skills/1")).andExpect(status().isUnauthorized());
    }
}
