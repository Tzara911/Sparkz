// Sparkz Group
// This file provides web addresses for working with skills.
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public List<Skill> getAllSkills() {
        return skillService.getAllSkills();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Skill> getSkillById(@PathVariable Long id) {
        Optional<Skill> skill = skillService.getSkillById(id);
        return skill.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<Skill> searchSkills(@RequestParam String type) {
        return skillService.getSkillsByType(type);
    }

    @GetMapping("/user/{email}")
    public List<Skill> getSkillsByUser(@PathVariable String email) {
        return skillService.getSkillsByUser(email);
    }

    @PostMapping
    public Skill addSkill(@RequestBody Skill skill) {
        return skillService.saveSkill(skill);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Skill> updateSkill(@PathVariable Long id, @RequestBody Skill newSkill) {
        Optional<Skill> foundSkill = skillService.getSkillById(id);

        if (foundSkill.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Skill skill = foundSkill.get();
        skill.setName(newSkill.getName());
        skill.setDescription(newSkill.getDescription());
        skill.setSkillType(newSkill.getSkillType());
        skill.setUserEmail(newSkill.getUserEmail());
        return ResponseEntity.ok(skillService.saveSkill(skill));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSkill(@PathVariable Long id) {
        if (skillService.getSkillById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        skillService.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }
}
