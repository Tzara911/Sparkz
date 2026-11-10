// Sparkz Group
// This file provides web addresses for working with a user's own skills.

package com.sparkz.sparkz.skills;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class UserSkillController {

    private final UserSkillService userSkillService;

    public UserSkillController(UserSkillService userSkillService) {
        this.userSkillService = userSkillService;
    }

    @GetMapping("/api/profiles/me/skills")
    public List<UserSkill> getOwnSkills(Principal principal) {
        return userSkillService.getOwnSkills(currentUserId(principal));
    }

    @PutMapping("/api/profiles/me/skills/{skillId}/{skillType}")
    public ResponseEntity<Void> addOwnSkill(
        Principal principal, @PathVariable Long skillId, @PathVariable String skillType) {
        AddSkillResult result = userSkillService.addOwnSkill(currentUserId(principal), skillId, skillType);
        if (result == AddSkillResult.ADDED) {
            return ResponseEntity.noContent().build();
        }
        if (result == AddSkillResult.INVALID_SKILL_TYPE) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/api/profiles/me/skills/{skillId}/{skillType}")
    public ResponseEntity<Void> removeOwnSkill(
        Principal principal, @PathVariable Long skillId, @PathVariable String skillType) {
        userSkillService.removeOwnSkill(currentUserId(principal), skillId, skillType);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
