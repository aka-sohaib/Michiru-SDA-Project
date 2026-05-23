package com.example.michiru.model;

// Exam ladder: tier order, question-bank difficulty per tier, labels/icons; shared by facade + UI.

public enum ProficiencyLadder {

    BEGINNER    ("Beginner",     "EASY",   "fas-seedling"),
    INTERMEDIATE("Intermediate", "MEDIUM", "fas-fire"),
    ADVANCED    ("Advanced",     "HARD",   "fas-bolt"),
    EXPERT      ("Expert",       "MIX",    "fas-crown");

    public static final int EXAM_QUESTION_COUNT = 10;

    private final String displayLabel;
    private final String questionDifficulty;
    private final String iconLiteral;

    ProficiencyLadder(String displayLabel, String questionDifficulty, String iconLiteral) {
        this.displayLabel       = displayLabel;
        this.questionDifficulty = questionDifficulty;
        this.iconLiteral        = iconLiteral;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getQuestionDifficulty() {
        return questionDifficulty;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }

    public int getLadderOrdinal() {
        return ordinal() + 1;
    }

    public static ProficiencyLadder[] ladder() {
        return values();
    }

    // Uppercase tier name → EASY/MEDIUM/HARD/MIX; unknown → EASY.
    public static String difficultyForTier(String tierName) {
        try {
            return valueOf(tierName).questionDifficulty;
        } catch (IllegalArgumentException e) {
            return "EASY";
        }
    }

    // NOVICE + ladder tiers (e.g. pickers that allow pre-ladder level).
    public static java.util.List<String> allLevelNames() {
        java.util.List<String> levels = new java.util.ArrayList<>();
        levels.add("NOVICE");
        for (ProficiencyLadder tier : values()) {
            levels.add(tier.name());
        }
        return java.util.Collections.unmodifiableList(levels);
    }

    // BEGINNER…EXPERT only.
    public static java.util.List<String> examTierNames() {
        java.util.List<String> tiers = new java.util.ArrayList<>();
        for (ProficiencyLadder tier : values()) {
            tiers.add(tier.name());
        }
        return java.util.Collections.unmodifiableList(tiers);
    }

    // Tiers with a single difficulty pool (excludes EXPERT/MIX for some authoring UIs).
    public static java.util.List<String> skillDifficultyTierNames() {
        java.util.List<String> tiers = new java.util.ArrayList<>();
        for (ProficiencyLadder tier : values()) {
            if (!"MIX".equals(tier.questionDifficulty)) {
                tiers.add(tier.name());
            }
        }
        return java.util.Collections.unmodifiableList(tiers);
    }

    public static int tierCount() {
        return values().length;
    }
}
