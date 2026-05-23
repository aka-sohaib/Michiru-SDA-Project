package com.example.michiru.model;

/**
 * Defines the Skill component in the Michiru application.
 */

public class Skill {

    private int    skillId;
    private String name;
    private String category;
    private String description;
    private String difficultyTier;
    private boolean isActive;
    private int    questionsRequiredToPass;
    private int    createdBy;
    private String createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Skill() {}

    public Skill(int skillId, String name, String category, String description,
                 String difficultyTier, boolean isActive,
                 int questionsRequiredToPass, int createdBy, String createdAt) {
        this.skillId                 = skillId;
        this.name                    = name;
        this.category                = category;
        this.description             = description;
        this.difficultyTier          = difficultyTier;
        this.isActive                = isActive;
        this.questionsRequiredToPass = questionsRequiredToPass;
        this.createdBy               = createdBy;
        this.createdAt               = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getSkillId()                 { return skillId; }
    public String  getName()                    { return name; }
    public String  getCategory()                { return category; }
    public String  getDescription()             { return description; }
    public String  getDifficultyTier()          { return difficultyTier; }
    public boolean isActive()                   { return isActive; }
    public int     getQuestionsRequiredToPass() { return questionsRequiredToPass; }
    public int     getCreatedBy()               { return createdBy; }
    public String  getCreatedAt()               { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setSkillId(int id)                       { this.skillId                 = id; }
    public void setName(String name)                     { this.name                    = name; }
    public void setCategory(String category)             { this.category                = category; }
    public void setDescription(String description)       { this.description             = description; }
    public void setDifficultyTier(String tier)           { this.difficultyTier          = tier; }
    public void setActive(boolean active)                { this.isActive                = active; }
    public void setQuestionsRequiredToPass(int count)    { this.questionsRequiredToPass = count; }
    public void setCreatedBy(int createdBy)              { this.createdBy               = createdBy; }
    public void setCreatedAt(String createdAt)           { this.createdAt               = createdAt; }

    @Override
    public String toString() {
        return "Skill{id=" + skillId + ", name='" + name + "', tier='" + difficultyTier + "', active=" + isActive + "}";
    }
}

