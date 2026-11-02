package com.sparkz.sparkz.acceptance;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filters, controllers and persistence; only the identity provider and DB are local fixtures. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mvp-acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=0",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/local-schema.sql",
    "spring.sql.init.data-locations=classpath:acceptance/seed.sql",
    "spring.main.allow-bean-definition-overriding=false",
    "sparkz.auth.audience=authenticated",
    "sparkz.matching.supported-schools=Demo University"
})
@AutoConfigureMockMvc
@ActiveProfiles("mvp-acceptance")
@Sql("/acceptance/seed.sql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AcceptanceSupport {
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    static final long JAVA = 4_000_000_001L;
    private static TestIssuer issuer;
    @Autowired MockMvc mvc;
    @Autowired JdbcClient db;

    @DynamicPropertySource
    static void issuerProperty(DynamicPropertyRegistry registry) {
        registry.add("sparkz.auth.issuer", () -> issuer().url);
    }

    private static synchronized TestIssuer issuer() {
        if (issuer == null) issuer = new TestIssuer();
        return issuer;
    }

    @AfterAll static synchronized void closeIssuer() {
        if (issuer != null) {
            issuer.server.stop(0);
            issuer = null;
        }
    }

    String token(UUID user) throws Exception { return token(user, Instant.now().plusSeconds(600)); }

    String token(UUID user, Instant expires) throws Exception {
        TestIssuer local = issuer();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("mvp").build(),
            new JWTClaimsSet.Builder().issuer(local.url).subject(user.toString()).audience("authenticated")
                .issueTime(Date.from(Instant.now().minusSeconds(600))).expirationTime(Date.from(expires)).build());
        jwt.sign(new RSASSASigner(local.key));
        return jwt.serialize();
    }

    ResultActions as(UUID user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token(user)));
    }

    MockHttpServletRequestBuilder sendBody(UUID receiver, long skill) {
        return post("/api/match-requests").contentType(MediaType.APPLICATION_JSON)
            .content("{\"receiverId\":\"" + receiver + "\",\"skillId\":" + skill + "}");
    }

    long send(UUID sender, UUID receiver, long skill) throws Exception {
        String body = as(sender, sendBody(receiver, skill)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.senderId").value(sender.toString()))
            .andExpect(jsonPath("$.receiverId").value(receiver.toString()))
            .andExpect(jsonPath("$.skillId").value(skill))
            .andExpect(jsonPath("$.status").value("Pending"))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions transition(UUID actor, long request, String next) throws Exception {
        return as(actor, patch("/api/match-requests/" + request).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"" + next + "\"}"));
    }

    void storedStatus(long request, String expected) {
        assertThat(db.sql("select status from match_requests where request_id = :id")
            .param("id", request).query(String.class).single()).isEqualTo(expected);
    }

    void visible(UUID viewer, UUID candidate, boolean expected) throws Exception {
        as(viewer, get("/api/discovery")).andExpect(status().isOk())
            .andExpect(jsonPath("$.profiles[*].userId", expected
                ? hasItem(candidate.toString()) : not(hasItem(candidate.toString()))));
    }

    private static final class TestIssuer {
        final RSAKey key;
        final HttpServer server;
        final String url;
        TestIssuer() {
            try {
                key = new RSAKeyGenerator(2048).keyID("mvp").generate();
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                url = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/v1";
                byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                server.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jwks.length);
                    try (var response = exchange.getResponseBody()) { response.write(jwks); }
                });
                server.start();
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot start isolated test JWT issuer", exception);
            }
        }
    }
}
