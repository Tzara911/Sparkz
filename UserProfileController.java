// Sparkz Group
// This file provides web addresses for working with user profiles.
// Emilee Schultz

package com.sparkz.sparkz;

import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public List<UserProfile> getAllUsers() {
        return userProfileService.getAllUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> getUserById(@PathVariable Long id) {
        Optional<UserProfile> user = userProfileService.getUserById(id);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public UserProfile addUser(@RequestBody UserProfile user) {
        return userProfileService.saveUser(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfile> updateUser(@PathVariable Long id, @RequestBody UserProfile newUser) {
        Optional<UserProfile> foundUser = userProfileService.getUserById(id);

        if (foundUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserProfile user = foundUser.get();
        user.setName(newUser.getName());
        user.setEmail(newUser.getEmail());
        user.setBio(newUser.getBio());
        return ResponseEntity.ok(userProfileService.saveUser(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (userProfileService.getUserById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        userProfileService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
