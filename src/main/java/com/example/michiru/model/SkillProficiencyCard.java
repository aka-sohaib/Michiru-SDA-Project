package com.example.michiru.model;

// Skill hub card: metadata + pass threshold + student's current belt for UC06 UI.

public class SkillProficiencyCard {

    private final int    skillId;
    private final String name;
    private final String category;
    private final String difficultyTier;
    private final int    questionsRequiredToPass;
    private final String currentLevel;

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

    public int    getSkillId()                 { return skillId; }
    public String getName()                    { return name; }
    public String getCategory()                { return category; }
    public String getDifficultyTier()          { return difficultyTier; }
    public int    getQuestionsRequiredToPass() { return questionsRequiredToPass; }
    public String getCurrentLevel()            { return currentLevel; }

    // 0 = NOVICE … 4 = EXPERT for progress UI.
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
