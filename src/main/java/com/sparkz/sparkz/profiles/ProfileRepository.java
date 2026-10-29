// Sparkz Group
// This file lets the application read and write profiles in the database.

package com.sparkz.sparkz.profiles;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;


@Repository
public class ProfileRepository {

    private final JdbcClient jdbcClient;

    public ProfileRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Profile> findByUserId(UUID userId) {
        return jdbcClient
            .sql(
                """
                SELECT user_id, username, email, first_name, last_name, school_name,
                       grade_year, address, phone, dob, created_at, time_zone,
                       account_status, bio, avatar_url, onboarding_completed, updated_at
                FROM profiles
                WHERE user_id = :userId
                """)
            .param("userId", userId)
            .query(Profile.class)
            .optional();
    }

    /**
     * Overwrites the editable fields of an existing profile. Returns the
     * updated Profile, or empty if no profile exists for that user id (so
     * the caller can turn that into a 404 instead of silently doing nothing).
     */
    public Optional<Profile> update(UUID userId, ProfileUpdateRequest request) {
        int rowsUpdated =
            jdbcClient
                .sql(
                    """
                    UPDATE profiles
                    SET username = :username,
                        email = :email,
                        first_name = :firstName,
                        last_name = :lastName,
                        school_name = :schoolName,
                        grade_year = :gradeYear,
                        address = :address,
                        phone = :phone,
                        dob = :dob,
                        time_zone = :timeZone,
                        bio = :bio,
                        avatar_url = :avatarUrl,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = :userId
                    """)
                .param("username", request.getUsername())
                .param("email", request.getEmail())
                .param("firstName", request.getFirstName())
                .param("lastName", request.getLastName())
                .param("schoolName", request.getSchoolName())
                .param("gradeYear", request.getGradeYear())
                .param("address", request.getAddress())
                .param("phone", request.getPhone())
                .param("dob", request.getDob())
                .param("timeZone", request.getTimeZone())
                .param("bio", request.getBio())
                .param("avatarUrl", request.getAvatarUrl())
                .param("userId", userId)
                .update();

        if (rowsUpdated == 0) {
            return Optional.empty();
        }
        return findByUserId(userId);
    }
}
