// Sparkz Group
// This file lets the application work with user profiles in the database.
// Emilee Schultz

package com.sparkz.sparkz;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    // Find a profile by the user's email address.
    Optional<UserProfile> findByEmail(String email);
}
