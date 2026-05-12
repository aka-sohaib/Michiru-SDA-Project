package com.example.michiru.model;

/**
 * Class definition for SkillAssignment.
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
    /**
     * Executes toString.
     */
    public String toString() {
        return "SkillAssignment{skillId=" + skillId + ", name='" + skillName +
               "', weight=" + weight + ", level='" + minimumProficiencyLevel + "'}";
    }
}

