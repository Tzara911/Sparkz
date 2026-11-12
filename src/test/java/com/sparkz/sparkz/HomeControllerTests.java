package com.sparkz.sparkz;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeControllerTests {
    @Autowired MockMvc mvc;

    @Test void anonymousEntryPointsReachDiscovery() throws Exception {
        for (String path : List.of("/", "/index.html")) {
            mvc.perform(get(path)).andExpect(status().isFound())
                .andExpect(redirectedUrl("/browse.html"));
        }
        for (String path : List.of("/browse.html", "/discovery.js", "/discovery.css",
                "/matches.html", "/matches.js", "/matches.css", "/sparkz-shell.js",
                "/sparkz-theme.css", "/fonts/dm-sans-latin-wght-normal.woff2",
                "/fonts/yellowtail-latin-400-normal.woff2", "/images/sparkz-logo.png")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test void publicFrontendDoesNotExposeAuthenticatedData() throws Exception {
        for (String path : List.of("/api/profiles/me", "/api/discovery", "/api/match-requests")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
