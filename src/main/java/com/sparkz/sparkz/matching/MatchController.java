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
    private final MatchOperations service;
    public MatchController(MatchOperations service) { this.service = service; }
    public record Send(@NotNull UUID receiverId, @NotNull @Positive Long skillId) {}
    public record Change(@NotNull MatchStatus status) {}
    public enum Direction { incoming, outgoing }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchRequest send(Principal principal, @Valid @RequestBody Send body) {
        return service.sendRequest(CurrentUser.id(principal), body.receiverId(), body.skillId());
    }

    @GetMapping
    public List<MatchRequest> list(Principal principal, @RequestParam Direction direction) {
        return service.list(CurrentUser.id(principal), direction == Direction.incoming);
    }

    @PatchMapping("/{id}")
    public MatchRequest change(Principal principal, @PathVariable @Positive long id, @Valid @RequestBody Change body) {
        UUID actor = CurrentUser.id(principal);
        return switch (body.status()) {
            case Accepted -> service.acceptRequest(actor, id);
            case Declined -> service.declineRequest(actor, id);
            case Canceled -> service.cancelRequest(actor, id);
            case Pending -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reset a request to pending");
        };
    }

}
