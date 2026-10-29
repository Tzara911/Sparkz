package com.sparkz.sparkz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "description")
    private String description;

    public Skill() {
    }

    public Skill(String skillName, String description) {
        this.skillName = skillName;
        this.description = description;
    }

    public Long getSkillId() { return skillId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
