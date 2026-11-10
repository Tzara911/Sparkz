// Sparkz Group
// This file stores a skill that a user has marked as something they teach or learn.

package com.sparkz.sparkz.skills;

/**
 * One skill that a specific user has marked as something they teach or
 * learn. Includes the skill's name (joined in from the skills catalog) so
 * the UI doesn't need a second call just to show a label.
 *
 * A plain class with getters and setters. The Repository fills it in when
 * it reads a row back from the "user_skills" table.
 */
public class UserSkill {

    private Long skillId;
    private String skillName;
    private String skillType;

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

    public String getSkillType() {
        return skillType;
    }

    public void setSkillType(String skillType) {
        this.skillType = skillType;
    }
}
