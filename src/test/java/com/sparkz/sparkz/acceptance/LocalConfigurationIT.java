package com.sparkz.sparkz.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Checks the demo's actual Basic-auth filter and CSRF contract, including duplicate configuration. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mvp-local;MODE=PostgreSQL;DB_CLOSE_DELAY=0",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/local-schema.sql",
    "spring.sql.init.data-locations=classpath:acceptance/seed.sql",
    "spring.main.allow-bean-definition-overriding=false",
    "sparkz.local.password=local-demo",
    "sparkz.matching.supported-schools=Demo University"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalConfigurationIT {
    @Autowired MockMvc mvc;

    @Test void demoLoginAndCsrfProtectedRequestsWorkWithOneSharedConfiguration() throws Exception {
        String alice = "00000000-0000-0000-0000-000000000001";
        String body = "{\"receiverId\":\"00000000-0000-0000-0000-000000000002\",\"skillId\":4000000001}";
        mvc.perform(get("/api/discovery")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/discovery").with(httpBasic(alice, "wrong-password")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/discovery").with(httpBasic(alice, "local-demo"))).andExpect(status().isOk());
        mvc.perform(post("/api/match-requests").with(httpBasic(alice, "local-demo"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/match-requests").with(httpBasic(alice, "local-demo")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }
}
