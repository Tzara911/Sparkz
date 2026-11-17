package com.sparkz.sparkz.sessions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/learning-sessions")
public class LearningSessionController {
    private final LearningSessionService service;
    public LearningSessionController(LearningSessionService service) { this.service = service; }

    public record Propose(@NotNull @Positive Long matchRequestId, @NotNull UUID teacherId,
            @NotNull OffsetDateTime scheduledStart, @NotNull OffsetDateTime scheduledEnd,
            @NotNull SessionMode mode, @Size(max = 2000) String location,
            @Size(max = 2000) String meetingUrl, @Size(max = 2000) String message) {}
    public record Change(@NotNull SessionStatus status) {}
    public enum Direction { all, incoming, outgoing }

    @PostMapping
    public ResponseEntity<LearningSession> propose(Principal principal, @Valid @RequestBody Propose body) {
        LearningSession session = service.propose(user(principal), body);
        return ResponseEntity.created(URI.create("/api/learning-sessions/" + session.id())).body(session);
    }

    @GetMapping
    public LearningSessionService.Page list(Principal principal,
            @RequestParam(defaultValue = "all") Direction direction,
            @RequestParam(required = false) @Positive Long matchRequestId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.list(user(principal), direction, matchRequestId, offset, limit);
    }

    @GetMapping("/{id}")
    public LearningSession get(Principal principal, @PathVariable @Positive long id) {
        return service.get(user(principal), id);
    }

    @PatchMapping("/{id}")
    public LearningSession change(Principal principal, @PathVariable @Positive long id,
            @Valid @RequestBody Change body) {
        return service.change(user(principal), id, body.status());
    }

    private UUID user(Principal principal) {
        if (principal == null || principal.getName() == null) throw new ResponseStatusException(UNAUTHORIZED);
        try { return UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(UNAUTHORIZED, "Invalid user identity"); }
    }
}
