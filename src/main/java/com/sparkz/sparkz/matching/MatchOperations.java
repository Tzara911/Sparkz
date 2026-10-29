package com.sparkz.sparkz.matching;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implements the SRS MatchOperations responsibilities using verified user UUIDs.
 * Request/skill IDs disambiguate the diagram's user-based operations; returned
 * records are HTTP results, not replacements for the shared User/Profile model.
 */
@Service
public class MatchOperations {
    private final MatchStore store;
    private final MatchEligibility eligibility;

    public MatchOperations(MatchStore store, MatchEligibility eligibility) {
        this.store = store;
        this.eligibility = eligibility;
    }

    @Transactional
    public MatchRequest sendRequest(UUID sender, UUID recipient, long skill) {
        if (sender.equals(recipient)) throw error(HttpStatus.BAD_REQUEST, "Cannot match with yourself");
        eligible(sender, recipient, skill);
        if (store.hasActive(sender, recipient))
            throw error(HttpStatus.CONFLICT, "A pending request or accepted match already exists");
        Instant now = Instant.now();
        return store.insert(sender, recipient, skill, now);
    }

    @Transactional(readOnly = true)
    public List<MatchRequest> list(UUID user, boolean incoming) { return store.list(user, incoming); }

    @Transactional
    public MatchRequest acceptRequest(UUID user, long requestId) {
        return change(user, requestId, MatchStatus.Accepted);
    }

    @Transactional
    public MatchRequest declineRequest(UUID user, long requestId) {
        return change(user, requestId, MatchStatus.Declined);
    }

    @Transactional
    public MatchRequest cancelRequest(UUID user, long requestId) {
        return change(user, requestId, MatchStatus.Canceled);
    }

    private MatchRequest change(UUID actor, long id, MatchStatus next) {
        MatchRequest request = store.find(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Request not found"));
        boolean participant = actor.equals(request.senderId()) || actor.equals(request.receiverId());
        if (!participant) throw error(HttpStatus.NOT_FOUND, "Request not found");
        if (next == MatchStatus.Pending) throw error(HttpStatus.BAD_REQUEST, "Cannot reset a request to pending");
        UUID authorized = next == MatchStatus.Canceled ? request.senderId() : request.receiverId();
        if (!actor.equals(authorized)) throw error(HttpStatus.FORBIDDEN, "You cannot perform this action");
        if (request.status() != MatchStatus.Pending) throw error(HttpStatus.CONFLICT, "Request is no longer pending");
        // Recheck eligibility on acceptance, since profiles/skills may have changed since sending.
        if (next == MatchStatus.Accepted) eligible(request.senderId(), request.receiverId(), request.skillId());
        Instant now = Instant.now();
        if (store.transition(id, next, now) != 1) throw error(HttpStatus.CONFLICT, "Request is no longer pending");
        return new MatchRequest(id, request.senderId(), request.receiverId(), request.skillId(), next, request.createdAt(), now);
    }

    private void eligible(UUID a, UUID b, long skill) {
        List<MatchStore.Profile> profiles = store.lockProfiles(a, b);
        eligibility.requirePair(profiles);
        if (store.blocked(a, b)) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "These users cannot match");
        if (!store.compatible(a, b, skill)) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "No compatible teaching and learning skills");
    }

    private static ResponseStatusException error(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
