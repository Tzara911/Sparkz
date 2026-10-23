package com.sparkz.sparkz.matching;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/match-requests")
public class MatchController {
    private final MatchService service;
    public MatchController(MatchService service) { this.service = service; }
    public record Send(@NotNull UUID receiverId, @NotNull @Positive Long skillId) {}
    public record Change(@NotNull MatchStatus status) {}
    public enum Direction { incoming, outgoing }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchRequest send(Principal principal, @Valid @RequestBody Send body) {
        return service.send(user(principal), body.receiverId(), body.skillId());
    }

    @GetMapping
    public List<MatchRequest> list(Principal principal, @RequestParam Direction direction) {
        return service.list(user(principal), direction == Direction.incoming);
    }

    @PatchMapping("/{id}")
    public MatchRequest change(Principal principal, @PathVariable @Positive long id, @Valid @RequestBody Change body) {
        return service.change(user(principal), id, body.status());
    }

    private UUID user(Principal principal) {
        if (principal == null || principal.getName() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try { return UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user identity"); }
    }
}
