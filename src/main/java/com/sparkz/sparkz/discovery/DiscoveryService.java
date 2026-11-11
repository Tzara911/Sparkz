package com.sparkz.sparkz.discovery;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DiscoveryService {
    private final JdbcClient db;
    private final Set<String> schools;
    public DiscoveryService(JdbcClient db, @Value("${sparkz.matching.supported-schools:}") List<String> schools) {
        this.db = db;
        this.schools = new HashSet<>(schools);
    }
    public record Skill(long skillId, String skillName, String skillType) {}
    public record Person(UUID userId, String username, String firstName, String schoolName,
        String bio, String avatarUrl, List<Skill> skills, List<Integer> availableDays,
        List<Skill> canLearn, List<Skill> canTeach, List<Integer> commonDays, boolean reciprocal,
        List<String> reasons, String matchStatus) {}
    public record Page(List<Person> profiles, int total, int offset, int limit) {}
    private record Profile(UUID id, String username, String firstName, String school, String bio, String avatar) {}
    public enum SkillType { teach, learn }

    @Transactional(readOnly = true)
    public Page browse(UUID viewer, Long skillId, SkillType skillType, Integer day, boolean recommended, int offset, int limit) {
        List<Person> people = eligible(viewer);
        var filtered = people.stream()
            .filter(p -> skillId == null && skillType == null || p.skills().stream().anyMatch(s ->
                (skillId == null || skillId == s.skillId()) && (skillType == null || skillType.name().equals(s.skillType()))))
            .filter(p -> day == null || p.availableDays().contains(day))
            .filter(p -> !recommended || !p.canLearn().isEmpty() || !p.canTeach().isEmpty())
            .sorted(Comparator.comparing(Person::reciprocal).reversed()
                .thenComparing(Comparator.comparingInt((Person p) -> p.canLearn().size() + p.canTeach().size()).reversed())
                .thenComparing(Comparator.comparingInt((Person p) -> p.commonDays().size()).reversed())
                .thenComparing(Person::userId)).toList();
        return new Page(filtered.stream().skip(offset).limit(limit).toList(), filtered.size(), offset, limit);
    }

    @Transactional(readOnly = true)
    public Person profile(UUID viewer, UUID target) {
        return eligible(viewer).stream().filter(p -> p.userId().equals(target)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    private List<Person> eligible(UUID viewer) {
        var viewerRows = db.sql("select school_name from profiles where user_id = :user and account_status = 'active' and onboarding_completed = true")
            .param("user", viewer).query((rs, row) -> rs.getString("school_name")).list();
        if (viewerRows.isEmpty() || !schools.contains(viewerRows.get(0)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Complete an active profile at a supported school to browse");
        var candidates = db.sql("""
            select p.user_id, p.username, p.first_name, p.school_name, p.bio, p.avatar_url from profiles p
            where p.user_id <> :user and p.school_name = :school
                and p.account_status = 'active' and p.onboarding_completed = true
                and not exists (select 1 from blocked_users b where
                    (b.blocker_id = :user and b.blocked_id = p.user_id)
                    or (b.blocker_id = p.user_id and b.blocked_id = :user))
            """).param("user", viewer).param("school", viewerRows.get(0)).query((rs, row) -> new Profile(
                rs.getObject("user_id", UUID.class), rs.getString("username"), rs.getString("first_name"),
                rs.getString("school_name"), rs.getString("bio"), rs.getString("avatar_url"))).list();
        if (candidates.isEmpty()) return List.of();
        List<UUID> ids = new ArrayList<>(candidates.stream().map(Profile::id).toList());
        ids.add(viewer);
        Map<UUID, List<Skill>> skills = new HashMap<>();
        db.sql("""
            select u.user_id, s.skill_id, s.skill_name, u.skill_type from user_skills u
            join skills s on s.skill_id = u.skill_id where u.user_id in (:ids) order by s.skill_id, u.skill_type
            """).param("ids", ids).query((rs, row) -> {
                skills.computeIfAbsent(rs.getObject("user_id", UUID.class), k -> new ArrayList<>())
                    .add(new Skill(rs.getLong("skill_id"), rs.getString("skill_name"), rs.getString("skill_type")));
                return 0;
            }).list();
        Map<UUID, List<Integer>> days = new HashMap<>();
        db.sql("select user_id, day_of_week from profile_available_days where user_id in (:ids) order by day_of_week")
            .param("ids", ids).query((rs, row) -> {
                days.computeIfAbsent(rs.getObject("user_id", UUID.class), k -> new ArrayList<>()).add(rs.getInt("day_of_week"));
                return 0;
            }).list();
        Map<UUID, String> statuses = new HashMap<>();
        db.sql("""
            select sender_id, receiver_id, status from match_requests where (sender_id = :user or receiver_id = :user)
                and status in ('pending', 'accepted') order by request_id
            """).param("user", viewer).query((rs, row) -> {
                UUID sender = rs.getObject("sender_id", UUID.class);
                UUID partner = sender.equals(viewer) ? rs.getObject("receiver_id", UUID.class) : sender;
                statuses.merge(partner, rs.getString("status"), (old, next) -> "accepted".equals(old) ? old : next);
                return 0;
            }).list();
        List<Skill> own = skills.getOrDefault(viewer, List.of());
        return candidates.stream().map(p -> {
            List<Skill> theirs = skills.getOrDefault(p.id(), List.of());
            List<Skill> learn = compatible(theirs, own, "teach", "learn");
            List<Skill> teach = compatible(theirs, own, "learn", "teach");
            List<Integer> available = days.getOrDefault(p.id(), List.of());
            List<Integer> common = available.stream().filter(days.getOrDefault(viewer, List.of())::contains).toList();
            boolean reciprocal = !learn.isEmpty() && !teach.isEmpty();
            List<String> reasons = new ArrayList<>();
            if (reciprocal) reasons.add("You can teach each other");
            for (Skill s : learn) reasons.add("You can learn " + s.skillName());
            for (Skill s : teach) reasons.add("You can teach " + s.skillName());
            if (!common.isEmpty()) reasons.add("Shared available days: " + common.stream().map(d -> java.time.DayOfWeek.of(d).getDisplayName(
                java.time.format.TextStyle.FULL, Locale.ENGLISH)).collect(java.util.stream.Collectors.joining(", ")));
            return new Person(p.id(), p.username(), p.firstName(), p.school(), p.bio(), p.avatar(), theirs,
                available, learn, teach, common, reciprocal, reasons, statuses.get(p.id()));
        }).toList();
    }

    private List<Skill> compatible(List<Skill> theirs, List<Skill> own, String theirType, String ownType) {
        return theirs.stream().filter(s -> s.skillType().equals(theirType) && own.stream().anyMatch(o ->
            o.skillId() == s.skillId() && o.skillType().equals(ownType))).toList();
    }
}
