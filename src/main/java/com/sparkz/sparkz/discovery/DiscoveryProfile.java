package com.sparkz.sparkz.discovery;

import java.util.List;
import java.util.UUID;

/** Explicit public projection. Never serialize a complete profiles row. */
public record DiscoveryProfile(UUID userId, String username, String bio, String schoolName,
        List<Skill> teachingSkills, List<Skill> learningSkills, List<Integer> availableDays,
        List<Skill> learnFromThem, List<Skill> teachThem, List<Integer> commonDays, boolean reciprocal) {
    public record Skill(long id, String name) {}
    public record Page(List<DiscoveryProfile> profiles, int page, int size, boolean hasNext) {}
}
