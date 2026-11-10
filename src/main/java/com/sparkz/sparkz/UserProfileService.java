package com.sparkz.sparkz;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public Optional<UserProfile> getUserById(UUID userId) {
        return userProfileRepository.findById(userId);
    }

    public UserProfile saveUser(UserProfile user) {
        return userProfileRepository.save(user);
    }

    public void deleteUser(UUID userId) {
        userProfileRepository.deleteById(userId);
    }
}
