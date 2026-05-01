package com.example.michiru.model;

/**
 * Domain model representing a row in the {@code questions} table.
 *
 * <p>Column mapping (snake_case → camelCase):</p>
 * <pre>
 *  question_id    → questionId     (INT UNSIGNED AUTO_INCREMENT)
 *  skill_id       → skillId        (INT UNSIGNED FK → skills, NOT NULL)
 *  question_text  → questionText   (TEXT NOT NULL)
 *  option_a       → optionA        (VARCHAR 1000 NOT NULL)
 *  option_b       → optionB        (VARCHAR 1000 NOT NULL)
 *  option_c       → optionC        (VARCHAR 1000 NOT NULL)
 *  option_d       → optionD        (VARCHAR 1000 NOT NULL)
 *  correct_option → correctOption  (ENUM A|B|C|D NOT NULL)
 *  difficulty_level → difficultyLevel (ENUM EASY|MEDIUM|HARD NOT NULL)
 *  is_active      → isActive       (TINYINT 1, default 1)
 *  created_by     → createdBy      (INT UNSIGNED, nullable FK → coordinators)
 * </pre>
 *
 * <p>Note: the {@code questions} table has no {@code created_at} column.</p>
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
    public String toString() {
        return "Question{id=" + questionId + ", skill=" + skillId +
               ", difficulty='" + difficultyLevel + "', active=" + isActive + "}";
    }
}
