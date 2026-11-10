// Sparkz Group
// This file stores a skill from the catalog - the full list of skills anyone can teach or learn.

package com.sparkz.sparkz.skills;


public class Skill {

    private Long skillId;
    private String skillName;
    private String description;

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
