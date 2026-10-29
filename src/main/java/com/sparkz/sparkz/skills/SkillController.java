// Sparkz Group
// This file provides web addresses for working with the skills catalog.

package com.sparkz.sparkz.skills;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/api/skills")
    public List<Skill> getAllSkills() {
        return skillService.getAllSkills();
    }
}
