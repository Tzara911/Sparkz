// Sparkz Group
// This file lets the application work with skills in the database.
// Emilee Schultz

package com.sparkz.sparkz;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    // Find all skills added by a specific user.
    List<Skill> findByUserEmail(String userEmail);

    // Find skills based on whether they are for teaching or learning.
    List<Skill> findBySkillTypeIgnoreCase(String skillType);
}
