package com.example.michiru.model;

/**
 * Lightweight DTO that combines a Skill's core fields with the student's
 * current (highest achieved) proficiency level for that skill.
 *
 * Used exclusively by the Skill Assessment hub view — not persisted directly.
 *
 * proficiency_level maps to ENUM: NOVICE → BEGINNER → INTERMEDIATE → ADVANCED → EXPERT
 */
public class SkillProficiencyCard {

    private final int    skillId;
    private final String name;
    private final String category;
    private final String difficultyTier;       // EASY | MEDIUM | HARD (skill's base level)
    private final int    questionsRequiredToPass;
    private final String currentLevel;         // student's highest achieved level for this skill

    public SkillProficiencyCard(int skillId,
                                String name,
                                String category,
                                String difficultyTier,
                                int    questionsRequiredToPass,
                                String currentLevel) {
        this.skillId                = skillId;
        this.name                   = name;
        this.category               = category;
        this.difficultyTier         = difficultyTier;
        this.questionsRequiredToPass = questionsRequiredToPass;
        this.currentLevel           = currentLevel;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int    getSkillId()                 { return skillId; }
    public String getName()                    { return name; }
    public String getCategory()                { return category; }
    public String getDifficultyTier()          { return difficultyTier; }
    public int    getQuestionsRequiredToPass() { return questionsRequiredToPass; }
    public String getCurrentLevel()            { return currentLevel; }

    /**
     * Returns the ordinal position of {@code currentLevel} in the 5-tier belt system
     * (0 = NOVICE, 4 = EXPERT).
     */
    public int getLevelOrdinal() {
        return switch (currentLevel) {
            case "NOVICE"        -> 0;
            case "BEGINNER"      -> 1;
            case "INTERMEDIATE"  -> 2;
            case "ADVANCED"      -> 3;
            case "EXPERT"        -> 4;
            default              -> 0;
        };
    }

    @Override
    public String toString() {
        return "SkillProficiencyCard{skillId=" + skillId
                + ", name='" + name + '\''
                + ", currentLevel='" + currentLevel + '\''
                + '}';
    }
}
