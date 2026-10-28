// Sparkz Group
// This file contains the basic actions for user profiles.
// Emilee Schultz

package com.sparkz.sparkz;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public List<UserProfile> getAllUsers() {
        return userProfileRepository.findAll();
    }

    public Optional<UserProfile> getUserById(Long id) {
        return userProfileRepository.findById(id);
    }

    public Optional<UserProfile> getUserByEmail(String email) {
        return userProfileRepository.findByEmail(email);
    }

    public UserProfile saveUser(UserProfile user) {
        return userProfileRepository.save(user);
    }

    public void deleteUser(Long id) {
        userProfileRepository.deleteById(id);
    }
}
