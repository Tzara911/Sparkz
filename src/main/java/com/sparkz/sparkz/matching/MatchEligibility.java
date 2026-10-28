package com.sparkz.sparkz.matching;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Shared account/school rules for discovery and match-request writes. */
@Component
public class MatchEligibility {
    private final Set<String> schools;
    public MatchEligibility(@Value("${sparkz.matching.supported-schools:}") List<String> schools) {
        this.schools = new HashSet<>(schools);
    }
    public void requireReady(MatchStore.Profile profile) {
        if (!"active".equals(profile.accountStatus()) || !profile.onboarded())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Accounts must be active and onboarded");
        if (profile.school() == null || !schools.contains(profile.school()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A supported school is required");
    }
    public void requirePair(List<MatchStore.Profile> profiles) {
        if (profiles.size() != 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        profiles.forEach(this::requireReady);
        if (!profiles.get(0).school().equals(profiles.get(1).school()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Both users must attend the same supported school");
    }
}
