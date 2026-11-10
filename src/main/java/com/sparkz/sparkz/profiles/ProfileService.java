// Sparkz Group
// This file contains the basic actions for a user's own profile.

package com.sparkz.sparkz.profiles;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;


@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public Optional<Profile> getOwnProfile(UUID userId) {
        return profileRepository.findByUserId(userId);
    }

    public Optional<Profile> updateOwnProfile(UUID userId, ProfileUpdateRequest request) {
        return profileRepository.update(userId, request);
    }
}
