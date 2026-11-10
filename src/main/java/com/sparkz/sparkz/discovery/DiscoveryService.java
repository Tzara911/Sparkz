package com.sparkz.sparkz.discovery;

import com.sparkz.sparkz.matching.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DiscoveryService {
    private final DiscoveryStore store;
    private final MatchEligibility eligibility;
    public DiscoveryService(DiscoveryStore store, MatchEligibility eligibility) {
        this.store = store; this.eligibility = eligibility;
    }
    @Transactional(readOnly = true)
    public DiscoveryProfile.Page discover(UUID viewer, boolean recommended, Long teaches, Long learns, Integer day, int page, int size) {
        var me = requireViewer(viewer);
        var rows = store.candidates(viewer, me.school(), recommended, teaches, learns, day, page * size, size + 1);
        boolean hasNext = rows.size() > size;
        if (hasNext) rows = rows.subList(0, size);
        if (rows.isEmpty()) return new DiscoveryProfile.Page(List.of(), page, size, false);
        List<UUID> users = new ArrayList<>();
        users.add(viewer);
        rows.forEach(row -> users.add(row.id()));
        // Batch fetch only the displayed page and viewer; no private time-slot query.
        var skills = store.skills(users);
        var days = store.days(users);
        var myTeach = skillList(skills, viewer, "teach");
        var myLearn = skillList(skills, viewer, "learn");
        var myDays = dayList(days, viewer);
        List<DiscoveryProfile> profiles = new ArrayList<>();
        for (var row : rows) {
            var teaching = skillList(skills, row.id(), "teach");
            var learning = skillList(skills, row.id(), "learn");
            var available = dayList(days, row.id());
            var learnFromThem = intersection(teaching, myLearn);
            var teachThem = intersection(learning, myTeach);
            var common = available.stream().filter(myDays::contains).toList();
            profiles.add(new DiscoveryProfile(row.id(), row.username(), row.bio(), row.school(), teaching, learning,
                available, learnFromThem, teachThem, common, !learnFromThem.isEmpty() && !teachThem.isEmpty()));
        }
        return new DiscoveryProfile.Page(profiles, page, size, hasNext);
    }
    @Transactional(readOnly = true)
    public List<DiscoveryProfile.Skill> catalog(UUID viewer) {
        requireViewer(viewer);
        return store.catalog();
    }
    private MatchStore.Profile requireViewer(UUID id) {
        var profile = store.viewer(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        eligibility.requireReady(profile);
        return profile;
    }
    private List<DiscoveryProfile.Skill> skillList(List<DiscoveryStore.UserSkill> skills, UUID user, String type) {
        return skills.stream().filter(s -> s.userId().equals(user) && s.type().equals(type)).map(DiscoveryStore.UserSkill::skill).toList();
    }
    private List<Integer> dayList(List<DiscoveryStore.UserDay> days, UUID user) {
        return days.stream().filter(d -> d.userId().equals(user)).map(DiscoveryStore.UserDay::day).toList();
    }
    private List<DiscoveryProfile.Skill> intersection(List<DiscoveryProfile.Skill> a, List<DiscoveryProfile.Skill> b) {
        Set<Long> ids = new HashSet<>();
        b.forEach(s -> ids.add(s.id()));
        return a.stream().filter(s -> ids.contains(s.id())).toList();
    }
}
