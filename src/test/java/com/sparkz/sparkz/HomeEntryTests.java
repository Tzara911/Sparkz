package com.sparkz.sparkz;

import com.sparkz.sparkz.matching.MatchSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(controllers = HomeController.class, properties = "sparkz.auth.issuer=")
@Import(MatchSecurity.class)
@ActiveProfiles("home-entry-check")
class HomeEntryTests {
    @Autowired MockMvc mvc;

    @Test void publicLandingPageWorksWhileUnconfiguredApisRemainClosed() throws Exception {
        for (String path : java.util.List.of("/", "/index.html")) {
            mvc.perform(get(path)).andExpect(status().isFound()).andExpect(redirectedUrl("/browse.html"));
        }
        mvc.perform(get("/browse.html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Find your people.")));
        mvc.perform(get("/sparkz-theme.css")).andExpect(status().isOk());
        mvc.perform(get("/images/sparkz-logo.png")).andExpect(status().isOk());
        mvc.perform(get("/api/discovery")).andExpect(status().isForbidden());
        mvc.perform(get("/api/profiles/me")).andExpect(status().isForbidden());
    }
}
