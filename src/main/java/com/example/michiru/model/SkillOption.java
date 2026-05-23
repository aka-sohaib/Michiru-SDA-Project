package com.example.michiru.model;

/**
 * Defines the SkillOption component in the Michiru application.
 */

public class SkillOption {

    private int    skillId;
    private String name;
    private String category;

    // ── Constructors ──────────────────────────────────────────────────────────

    public SkillOption() {}

    public SkillOption(int skillId, String name, String category) {
        this.skillId  = skillId;
        this.name     = name;
        this.category = category;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int    getSkillId()   { return skillId; }
    public String getName()      { return name; }
    public String getCategory()  { return category; }

    public void setSkillId(int id)       { this.skillId  = id; }
    public void setName(String name)     { this.name     = name; }
    public void setCategory(String cat)  { this.category = cat; }

    /**
     * Used directly by {@link javafx.scene.control.ComboBox} for display text.
     * Format: "Skill Name  (Category)"
     */
    @Override
    public String toString() {
        return name + "  (" + category + ")";
    }
}

