package com.example.michiru.model;

/**
 * Domain model representing a row in {@code internship_templates}.
 *
 * <p>Column mapping (snake_case → camelCase):</p>
 * <pre>
 *  template_id  → templateId   (INT UNSIGNED AUTO_INCREMENT)
 *  name         → name         (VARCHAR 255, UNIQUE)
 *  description  → description  (TEXT, nullable)
 *  is_active    → isActive     (TINYINT 1, default 1)
 *  created_by   → createdBy    (INT UNSIGNED, nullable FK → coordinators)
 *  created_at   → createdAt    (DATETIME, DB default)
 * </pre>
 *
 * <p>{@code skillCount} is a denormalized read-only field populated by a
 * COUNT JOIN in {@code MySQLHandler.getAllInternshipTemplates()} — it is
 * never written back to the database.</p>
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
    public String toString() {
        return "InternshipTemplate{id=" + templateId + ", name='" + name + "', active=" + isActive + "}";
    }
}
