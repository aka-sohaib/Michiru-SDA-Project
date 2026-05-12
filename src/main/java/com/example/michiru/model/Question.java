package com.example.michiru.model;

/**
 * Class definition for Question.
 */

public class Question {

    private int    questionId;
    private int    skillId;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctOption;
    private String difficultyLevel;
    private boolean isActive;
    private int    createdBy;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Question() {}

    public Question(int questionId, int skillId, String questionText,
                    String optionA, String optionB, String optionC, String optionD,
                    String correctOption, String difficultyLevel,
                    boolean isActive, int createdBy) {
        this.questionId     = questionId;
        this.skillId        = skillId;
        this.questionText   = questionText;
        this.optionA        = optionA;
        this.optionB        = optionB;
        this.optionC        = optionC;
        this.optionD        = optionD;
        this.correctOption  = correctOption;
        this.difficultyLevel = difficultyLevel;
        this.isActive       = isActive;
        this.createdBy      = createdBy;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getQuestionId()    { return questionId; }
    public int     getSkillId()       { return skillId; }
    public String  getQuestionText()  { return questionText; }
    public String  getOptionA()       { return optionA; }
    public String  getOptionB()       { return optionB; }
    public String  getOptionC()       { return optionC; }
    public String  getOptionD()       { return optionD; }
    public String  getCorrectOption() { return correctOption; }
    public String  getDifficultyLevel() { return difficultyLevel; }
    public boolean isActive()         { return isActive; }
    public int     getCreatedBy()     { return createdBy; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setQuestionId(int id)            { this.questionId     = id; }
    public void setSkillId(int skillId)          { this.skillId        = skillId; }
    public void setQuestionText(String text)     { this.questionText   = text; }
    public void setOptionA(String a)             { this.optionA        = a; }
    public void setOptionB(String b)             { this.optionB        = b; }
    public void setOptionC(String c)             { this.optionC        = c; }
    public void setOptionD(String d)             { this.optionD        = d; }
    public void setCorrectOption(String opt)     { this.correctOption  = opt; }
    public void setDifficultyLevel(String level) { this.difficultyLevel = level; }
    public void setActive(boolean active)        { this.isActive       = active; }
    public void setCreatedBy(int id)             { this.createdBy      = id; }

    @Override
    /**
     * Executes toString.
     */
    public String toString() {
        return "Question{id=" + questionId + ", skill=" + skillId +
               ", difficulty='" + difficultyLevel + "', active=" + isActive + "}";
    }
}

