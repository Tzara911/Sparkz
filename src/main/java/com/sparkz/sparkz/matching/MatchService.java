package com.sparkz.sparkz.matching;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchService {
    private final MatchStore store;
    private final Set<String> schools;

    public MatchService(MatchStore store, @Value("${sparkz.matching.supported-schools:}") List<String> schools) {
        this.store = store;
        this.schools = new HashSet<>(schools);
    }

    @Transactional
    public MatchRequest send(UUID sender, UUID recipient, long skill) {
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
    public MatchRequest change(UUID actor, long id, MatchStatus next) {
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
        if (profiles.size() != 2) throw error(HttpStatus.NOT_FOUND, "Profile not found");
        for (MatchStore.Profile profile : profiles) {
            if (!"active".equals(profile.accountStatus()) || !profile.onboarded())
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Both accounts must be active and onboarded");
        }
        String school = profiles.get(0).school();
        if (school == null || !schools.contains(school) || !school.equals(profiles.get(1).school()))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Both users must attend the same supported school");
        if (store.blocked(a, b)) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "These users cannot match");
        if (!store.compatible(a, b, skill)) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "No compatible teaching and learning skills");
    }

    private static ResponseStatusException error(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
