package com.sparkz.sparkz.matching;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentUser {
    private CurrentUser() {}
    public static UUID id(Principal principal) {
        if (principal == null || principal.getName() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try { return UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user identity");
        }
    }
}
