package com.sparkz.sparkz;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // The configured authentication system must set Principal.getName() to the Supabase Auth UUID.
    @GetMapping
    public ResponseEntity<UserProfile> getMyProfile(Principal principal) {
        UUID userId = getAuthenticatedUserId(principal);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return userProfileService.getUserById(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Profile IDs come from the authenticated identity, never from the request body.
    @PutMapping
    public ResponseEntity<UserProfile> updateMyProfile(Principal principal, @RequestBody UserProfile updates) {
        UUID userId = getAuthenticatedUserId(principal);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        UserProfile profile = userProfileService.getUserById(userId).orElseGet(UserProfile::new);
        profile.setUserId(userId);
        profile.setFirstName(updates.getFirstName());
        profile.setLastName(updates.getLastName());
        profile.setEmail(updates.getEmail());
        profile.setBio(updates.getBio());
        return ResponseEntity.ok(userProfileService.saveUser(profile));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMyProfile(Principal principal) {
        UUID userId = getAuthenticatedUserId(principal);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (userProfileService.getUserById(userId).isEmpty()) return ResponseEntity.notFound().build();
        userProfileService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getAuthenticatedUserId(Principal principal) {
        if (principal == null || principal.getName() == null) return null;
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
