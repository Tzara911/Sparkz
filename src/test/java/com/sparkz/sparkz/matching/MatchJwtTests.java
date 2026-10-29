package com.sparkz.sparkz.matching;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MatchController.class)
@Import(MatchSecurity.class)
@ActiveProfiles("jwt-check")
class MatchJwtTests {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final RSAKey RSA;
    private static final ECKey EC;
    private static final HttpServer SERVER;
    private static final String ISSUER;
    static {
        try {
            RSA = new RSAKeyGenerator(2048).keyID("rsa").generate();
            EC = new ECKeyGenerator(Curve.P_256).keyID("ec").generate();
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ISSUER = "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/auth/v1";
            byte[] jwks = new JWKSet(List.of(RSA.toPublicJWK(), EC.toPublicJWK()))
                .toString().getBytes(StandardCharsets.UTF_8);
            SERVER.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var body = exchange.getResponseBody()) { body.write(jwks); }
            });
            SERVER.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("sparkz.auth.issuer", () -> ISSUER);
    }
    @AfterAll static void stop() { SERVER.stop(0); }
    @Autowired MockMvc mvc;
    @MockitoBean MatchOperations service;

    @Test void acceptsRs256AndEs256WithVerifiedSubject() throws Exception {
        when(service.list(USER, true)).thenReturn(List.of());
        for (boolean ec : List.of(false, true)) {
            mvc.perform(get("/api/match-requests?direction=incoming")
                    .header("Authorization", "Bearer " + token(ec, b -> {})))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        verify(service, times(2)).list(USER, true);
    }

    @Test void rejectsMissingOrMalformedTokens() throws Exception {
        mvc.perform(get("/api/match-requests?direction=incoming"))
            .andExpect(status().isUnauthorized());
        reject("forged-token");
        verifyNoInteractions(service);
    }

    @Test void rejectsWrongIssuerAudienceExpiredAndFutureTokens() throws Exception {
        reject(token(false, b -> b.issuer("https://another-project.example/auth/v1")));
        reject(token(false, b -> b.audience("another-audience")));
        reject(token(false, b -> b.expirationTime(Date.from(Instant.now().minusSeconds(300)))));
        reject(token(true, b -> b.notBeforeTime(Date.from(Instant.now().plusSeconds(300)))));
        verifyNoInteractions(service);
    }

    @Test void requiresExpirationAndUuidSubject() throws Exception {
        reject(token(false, b -> b.expirationTime(null)));
        reject(token(true, b -> b.subject(null)));
        reject(token(false, b -> b.subject("not-a-user-uuid")));
        verifyNoInteractions(service);
    }

    @Test void rejectsSignatureFromUntrustedKey() throws Exception {
        SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa").build(), claims(b -> {}));
        forged.sign(new RSASSASigner(new RSAKeyGenerator(2048).generate()));
        reject(forged.serialize());
        verifyNoInteractions(service);
    }

    private JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> change) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(ISSUER).subject(USER.toString())
            .audience("authenticated").issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(600)));
        change.accept(builder);
        return builder.build();
    }

    private String token(boolean ec, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(ec ? JWSAlgorithm.ES256 : JWSAlgorithm.RS256)
            .keyID(ec ? "ec" : "rsa").build(), claims(change));
        jwt.sign(ec ? new ECDSASigner(EC) : new RSASSASigner(RSA));
        return jwt.serialize();
    }

    private void reject(String token) throws Exception {
        mvc.perform(get("/api/match-requests?direction=incoming").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
}
