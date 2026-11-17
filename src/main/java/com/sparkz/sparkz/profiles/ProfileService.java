// Sparkz Group
// This file contains the basic actions for a user's own profile.

package com.sparkz.sparkz.profiles;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


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
        Optional<Profile> updatedProfile;
        try {
            updatedProfile = profileRepository.update(userId, request);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
        if (updatedProfile.isEmpty()) {
            return updatedProfile;
        }

        Profile profile = updatedProfile.get();
        if (!profile.isOnboardingCompleted() && hasRequiredOnboardingFields(profile)) {
            return profileRepository.completeOnboarding(userId);
        }
        return updatedProfile;
    }

    /**
     * Onboarding is considered complete once these fields are filled in.
     * This only ever turns onboarding_completed on - it is a one-time
     * event, not something later edits can turn back off.
     */
    private boolean hasRequiredOnboardingFields(Profile profile) {
        return isFilled(profile.getUsername())
            && isFilled(profile.getEmail())
            && isFilled(profile.getFirstName())
            && isFilled(profile.getLastName())
            && isFilled(profile.getSchoolName())
            && isFilled(profile.getGradeYear())
            && isFilled(profile.getAddress())
            && isFilled(profile.getPhone());
    }

    private boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }

    public Optional<Profile> activateOwnAccount(UUID userId) {
        return profileRepository.updateAccountStatus(userId, "active");
    }

    public Optional<Profile> deactivateOwnAccount(UUID userId) {
        return profileRepository.updateAccountStatus(userId, "deactivated");
    }
}
