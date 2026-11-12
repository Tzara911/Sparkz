package com.sparkz.sparkz.matching;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// Full component scanning must register the configuration and its filter as distinct beans.
// WebMvcTest imports the configuration differently and does not catch that name collision.
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:jwt-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.mode=never",
    "sparkz.auth.issuer="
})
@AutoConfigureMockMvc
@ActiveProfiles("security-check")
class MatchSecurityStartupTests {
    @Autowired MockMvc mvc;

    @Test void publicFrontendStillLoadsWithoutLocalSecurityOrAnIssuer() throws Exception {
        for (String path : java.util.List.of("/", "/index.html")) {
            mvc.perform(get(path)).andExpect(status().isFound())
                .andExpect(redirectedUrl("/browse.html"));
        }
        mvc.perform(get("/browse.html")).andExpect(status().isOk());
        mvc.perform(get("/sparkz-shell.js")).andExpect(status().isOk());
    }

    @Test void fullApplicationStartsAndDeniesProtectedRoutesWithoutAnIssuer() throws Exception {
        for (String path : java.util.List.of("/api/match-requests", "/api/users/me",
                "/api/profiles/me", "/api/skills")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }
}
