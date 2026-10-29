package com.sparkz.sparkz.discovery;

import java.util.List;
import java.util.UUID;

/** Explicit public projection. Never serialize a complete profiles row. */
public record DiscoveryProfile(UUID userId, String username, String bio, String schoolName,
        List<Skill> teachingSkills, List<Skill> learningSkills, List<Integer> availableDays,
        List<Skill> learnFromThem, List<Skill> teachThem, List<Integer> commonDays, boolean reciprocal) {
    /** Public skill DTO: domain skillID/skillName map to API id/name; IDs retain SQL bigint precision. */
    public record Skill(long id, String name, String description) {}
    public record Page(List<DiscoveryProfile> profiles, int page, int size, boolean hasNext) {}
}
