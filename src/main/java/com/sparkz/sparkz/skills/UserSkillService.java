// Sparkz Group
// This file contains the basic actions for a user's own list of skills.

package com.sparkz.sparkz.skills;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;


@Service
public class UserSkillService {

    private static final Set<String> VALID_SKILL_TYPES = Set.of("teach", "learn");

    private final UserSkillRepository userSkillRepository;
    private final SkillService skillService;

    public UserSkillService(UserSkillRepository userSkillRepository, SkillService skillService) {
        this.userSkillRepository = userSkillRepository;
        this.skillService = skillService;
    }

    public List<UserSkill> getOwnSkills(UUID userId) {
        return userSkillRepository.findByUserId(userId);
    }

    public AddSkillResult addOwnSkill(UUID userId, Long skillId, String skillType) {
        if (!VALID_SKILL_TYPES.contains(skillType)) {
            return AddSkillResult.INVALID_SKILL_TYPE;
        }
        if (!skillService.skillExists(skillId)) {
            return AddSkillResult.SKILL_NOT_FOUND;
        }
        userSkillRepository.add(userId, skillId, skillType);
        return AddSkillResult.ADDED;
    }

    public void removeOwnSkill(UUID userId, Long skillId, String skillType) {
        userSkillRepository.remove(userId, skillId, skillType);
    }
}
