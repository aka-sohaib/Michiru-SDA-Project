package com.example.michiru.model;

/**
 * Class definition for ReadinessSkillResult.
 */

public class ReadinessSkillResult {

    private final int    skillId;
    private final String skillName;
    private final String skillCategory;
    private final String currentLevel;   // student's highest achieved level; default "NOVICE"
    private final String requiredLevel;  // minimum_proficiency_level from internship_skill_requirements
    private final int    weight;         // relative importance from internship_skill_requirements
    private final double skillScore;     // 0.0 – 1.0  (capped at 1.0 for over-achievers)
    private final String gapStatus;      // NO_GAP | MINOR_GAP | MAJOR_GAP

    public ReadinessSkillResult(int skillId,
                                String skillName,
                                String skillCategory,
                                String currentLevel,
                                String requiredLevel,
                                int    weight,
                                double skillScore,
                                String gapStatus) {
        this.skillId       = skillId;
        this.skillName     = skillName;
        this.skillCategory = skillCategory;
        this.currentLevel  = currentLevel;
        this.requiredLevel = requiredLevel;
        this.weight        = weight;
        this.skillScore    = skillScore;
        this.gapStatus     = gapStatus;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int    getSkillId()       { return skillId; }
    public String getSkillName()     { return skillName; }
    public String getSkillCategory() { return skillCategory; }
    public String getCurrentLevel()  { return currentLevel; }
    public String getRequiredLevel() { return requiredLevel; }
    public int    getWeight()        { return weight; }
    public double getSkillScore()    { return skillScore; }
    public String getGapStatus()     { return gapStatus; }

    /** Convenience: percentage 0–100 rounded to one decimal place. */
    public double getSkillScorePct() { return skillScore * 100.0; }

    @Override
    /**
     * Executes toString.
     */
    public String toString() {
        return "ReadinessSkillResult{skill='" + skillName
                + "', current=" + currentLevel
                + ", required=" + requiredLevel
                + ", score=" + String.format("%.1f%%", getSkillScorePct())
                + ", gap=" + gapStatus + '}';
    }
}

