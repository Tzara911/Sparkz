package com.sparkz.sparkz.matching;

import java.security.Principal;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile({"local", "test"})
public class MatchSessionController {
    @GetMapping("/api/match-session")
    public Map<String, String> session(Principal principal, CsrfToken token) {
        return Map.of("userId", principal.getName(), "csrfHeader", token.getHeaderName(), "csrfToken", token.getToken());
    }
}
