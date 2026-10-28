// Sparkz Group
// This file contains the basic actions for skills.
// Emilee Schultz

package com.sparkz.sparkz;

import java.util.List;
import java.util.Optional;
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

    public Optional<Skill> getSkillById(Long id) {
        return skillRepository.findById(id);
    }

    public List<Skill> getSkillsByUser(String userEmail) {
        return skillRepository.findByUserEmail(userEmail);
    }

    public List<Skill> getSkillsByType(String skillType) {
        return skillRepository.findBySkillTypeIgnoreCase(skillType);
    }

    public Skill saveSkill(Skill skill) {
        return skillRepository.save(skill);
    }

    public void deleteSkill(Long id) {
        skillRepository.deleteById(id);
    }
}
