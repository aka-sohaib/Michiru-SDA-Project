package com.example.michiru.model;

/**
 * Class definition for InternshipTemplate.
 */

public class InternshipTemplate {

    private int    templateId;
    private String name;
    private String description;
    private boolean isActive;
    private int    createdBy;
    private String createdAt;

    /** Denormalized: total rows in internship_skill_requirements for this template. */
    private int skillCount;

    // ── Constructors ──────────────────────────────────────────────────────────

    public InternshipTemplate() {}

    public InternshipTemplate(int templateId, String name, String description,
                              boolean isActive, int createdBy,
                              String createdAt, int skillCount) {
        this.templateId  = templateId;
        this.name        = name;
        this.description = description;
        this.isActive    = isActive;
        this.createdBy   = createdBy;
        this.createdAt   = createdAt;
        this.skillCount  = skillCount;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getTemplateId()  { return templateId; }
    public String  getName()        { return name; }
    public String  getDescription() { return description; }
    public boolean isActive()       { return isActive; }
    public int     getCreatedBy()   { return createdBy; }
    public String  getCreatedAt()   { return createdAt; }
    public int     getSkillCount()  { return skillCount; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setTemplateId(int templateId)    { this.templateId  = templateId; }
    public void setName(String name)             { this.name        = name; }
    public void setDescription(String desc)      { this.description = desc; }
    public void setActive(boolean active)        { this.isActive    = active; }
    public void setCreatedBy(int createdBy)      { this.createdBy   = createdBy; }
    public void setCreatedAt(String createdAt)   { this.createdAt   = createdAt; }
    public void setSkillCount(int skillCount)    { this.skillCount  = skillCount; }

    @Override
    /**
     * Executes toString.
     */
    public String toString() {
        return "InternshipTemplate{id=" + templateId + ", name='" + name + "', active=" + isActive + "}";
    }
}

