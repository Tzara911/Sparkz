// Sparkz Group
// This file contains the basic actions for the skills catalog.

package com.sparkz.sparkz.skills;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    public boolean skillExists(Long skillId) {
        return skillRepository.existsById(skillId);
    }
}
