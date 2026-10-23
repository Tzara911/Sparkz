package com.sparkz.sparkz.matching;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MatchController.class, properties = "sparkz.auth.issuer=")
@Import(MatchSecurity.class)
@ActiveProfiles("security-check")
class MatchSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean MatchService service;

    @Test void missingIssuerDeniesAnonymousAndForgedBearerTokens() throws Exception {
        mvc.perform(get("/api/match-requests?direction=incoming"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/match-requests?direction=incoming")
                .header("Authorization", "Bearer forged-token"))
            .andExpect(status().isForbidden());
    }
}
