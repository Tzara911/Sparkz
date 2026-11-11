package com.sparkz.sparkz.availability;

import java.time.LocalTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvailabilityService {
    private final JdbcClient db;
    public AvailabilityService(JdbcClient db) { this.db = db; }
    public record Slot(long slotId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
    public record Availability(List<Integer> days, List<Slot> slots, String timeZone) {}

    @Transactional(readOnly = true)
    public Availability own(UUID user) {
        String zone = profile(user, false);
        return new Availability(days(user), db.sql("""
            select slot_id, day_of_week, start_time, end_time from profile_available_slots
            where user_id = :user order by day_of_week, start_time, slot_id
            """).param("user", user).query((rs, row) -> new Slot(rs.getLong("slot_id"),
                rs.getInt("day_of_week"), rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class))).list(), zone);
    }

    public List<Integer> days(UUID user) {
        return db.sql("select day_of_week from profile_available_days where user_id = :user order by day_of_week")
            .param("user", user).query(Integer.class).list();
    }

    @Transactional
    public Availability replaceDays(UUID user, List<Integer> days) {
        profile(user, true);
        Set<Integer> wanted = new TreeSet<>(days);
        // Remove child slots before removing a public day; retain slots for unchanged days.
        for (int day : days(user)) if (!wanted.contains(day)) {
            db.sql("delete from profile_available_slots where user_id = :user and day_of_week = :day")
                .param("user", user).param("day", day).update();
            db.sql("delete from profile_available_days where user_id = :user and day_of_week = :day")
                .param("user", user).param("day", day).update();
        }
        List<Integer> existing = days(user);
        for (int day : wanted) if (!existing.contains(day)) {
            db.sql("insert into profile_available_days(user_id, day_of_week) values (:user, :day)")
                .param("user", user).param("day", day).update();
        }
        return own(user);
    }

    @Transactional
    public Slot saveSlot(UUID user, Long id, int day, LocalTime start, LocalTime end) {
        profile(user, true); // Serialize same-user edits and day replacement.
        if (id != null && db.sql("select count(*) from profile_available_slots where user_id = :user and slot_id = :id")
                .param("user", user).param("id", id).query(Long.class).single() == 0) throw missing();
        if (!start.isBefore(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be before end time");
        if (!days(user).contains(day)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select the available day first");
        if (id == null) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            db.sql("""
                insert into profile_available_slots(user_id, day_of_week, start_time, end_time)
                values (:user, :day, :start, :end)
                """).param("user", user).param("day", day).param("start", start).param("end", end)
                .update(keys, "slot_id");
            id = Objects.requireNonNull(keys.getKey()).longValue();
        } else {
            db.sql("""
                update profile_available_slots set day_of_week = :day, start_time = :start, end_time = :end
                where user_id = :user and slot_id = :id
                """).param("user", user).param("id", id).param("day", day).param("start", start).param("end", end).update();
        }
        return new Slot(id, day, start, end);
    }

    @Transactional
    public void deleteSlot(UUID user, long id) {
        profile(user, true);
        if (db.sql("delete from profile_available_slots where user_id = :user and slot_id = :id")
                .param("user", user).param("id", id).update() == 0) throw missing();
    }

    private String profile(UUID user, boolean lock) {
        var rows = db.sql("select time_zone, account_status from profiles where user_id = :user" + (lock ? " for update" : ""))
            .param("user", user).query((rs, row) -> new String[]{rs.getString("time_zone"), rs.getString("account_status")}).list();
        if (rows.isEmpty()) throw missing();
        if (!"active".equals(rows.get(0)[1])) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        return rows.get(0)[0];
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Availability not found"); }
}
