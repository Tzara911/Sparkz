package com.sparkz.sparkz.profiles;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the stand-in login works end to end: wrong credentials are rejected,
 * correct credentials are accepted. What the profile endpoint actually
 * returns once authenticated is ProfileControllerTests' job, not this
 * class's - this class only cares about authentication itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalLoginConfigTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/profiles/me").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "wrong-password")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void correctDemoCredentialsPassAuthentication() throws Exception {
        mockMvc.perform(get("/api/profiles/me").with(httpBasic(LocalLoginConfig.DEMO_USER_1, "local-demo")))
            .andExpect(status().isOk());
    }
}
