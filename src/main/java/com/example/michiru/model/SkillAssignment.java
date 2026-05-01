package com.example.michiru.model;

/**
 * Domain model representing a row in {@code internship_skill_requirements},
 * enriched with the skill's display name and category via a JOIN.
 *
 * <p>Column mapping:</p>
 * <pre>
 *  requirement_id            → requirementId          (INT UNSIGNED AUTO_INCREMENT)
 *  template_id               → templateId             (INT UNSIGNED FK)
 *  skill_id                  → skillId                (INT UNSIGNED FK)
 *  weight                    → weight                 (INT UNSIGNED DEFAULT 1)
 *  minimum_proficiency_level → minimumProficiencyLevel (ENUM)
 *  status                    → status                 (ENUM ACTIVE | INACTIVE)
 * </pre>
 *
 * <p>{@code skillName} and {@code skillCategory} are populated via a JOIN
 * on the {@code skills} table and are never written back directly.</p>
 */
public class SkillAssignment {

    private int    requirementId;
    private int    templateId;
    private int    skillId;
    private String skillName;
    private String skillCategory;
    private int    weight;
    private String minimumProficiencyLevel;
    private String status;

    // ── Constructors ──────────────────────────────────────────────────────────

    public SkillAssignment() {}

    public SkillAssignment(int requirementId, int templateId, int skillId,
                           String skillName, String skillCategory,
                           int weight, String minimumProficiencyLevel, String status) {
        this.requirementId          = requirementId;
        this.templateId             = templateId;
        this.skillId                = skillId;
        this.skillName              = skillName;
        this.skillCategory          = skillCategory;
        this.weight                 = weight;
        this.minimumProficiencyLevel = minimumProficiencyLevel;
        this.status                 = status;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int    getRequirementId()           { return requirementId; }
    public int    getTemplateId()              { return templateId; }
    public int    getSkillId()                 { return skillId; }
    public String getSkillName()               { return skillName; }
    public String getSkillCategory()           { return skillCategory; }
    public int    getWeight()                  { return weight; }
    public String getMinimumProficiencyLevel() { return minimumProficiencyLevel; }
    public String getStatus()                  { return status; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setRequirementId(int id)               { this.requirementId = id; }
    public void setTemplateId(int id)                  { this.templateId = id; }
    public void setSkillId(int id)                     { this.skillId = id; }
    public void setSkillName(String name)              { this.skillName = name; }
    public void setSkillCategory(String cat)           { this.skillCategory = cat; }
    public void setWeight(int weight)                  { this.weight = weight; }
    public void setMinimumProficiencyLevel(String lvl) { this.minimumProficiencyLevel = lvl; }
    public void setStatus(String status)               { this.status = status; }

    @Override
    public String toString() {
        return "SkillAssignment{skillId=" + skillId + ", name='" + skillName +
               "', weight=" + weight + ", level='" + minimumProficiencyLevel + "'}";
    }
}
