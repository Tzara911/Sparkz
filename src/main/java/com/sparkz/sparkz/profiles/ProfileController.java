// Sparkz Group
// This file provides web addresses for working with a user's own profile.

package com.sparkz.sparkz.profiles;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;


@RestController
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/api/profiles/me")
    public ResponseEntity<Profile> getOwnProfile(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        Optional<Profile> profile = profileService.getOwnProfile(userId);
        if (profile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile.get());
    }

    @PutMapping("/api/profiles/me")
    public ResponseEntity<Profile> updateOwnProfile(
        Principal principal, @Valid @RequestBody ProfileUpdateRequest request) {
        UUID userId = UUID.fromString(principal.getName());
        Optional<Profile> updatedProfile = profileService.updateOwnProfile(userId, request);
        if (updatedProfile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updatedProfile.get());
    }
}
