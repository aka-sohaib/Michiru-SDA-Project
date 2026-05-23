package com.example.michiru.model;

/**
 * Defines the SkillProficiency component in the Michiru application.
 */

import java.time.LocalDate;

public class SkillProficiency {

    private int       studentId;
    private int       skillId;
    private String    currentLevel;
    private LocalDate lastAssessmentDate;

    public SkillProficiency() {}

    public SkillProficiency(int studentId, int skillId, String currentLevel) {
        this.studentId    = studentId;
        this.skillId      = skillId;
        this.currentLevel = currentLevel;
    }

    /** UC11 step 3.1.1.1 — updates proficiency level (delegates to setLevel). */
    public void updateProficiencyLevel(String level) {
        setLevel(level);
    }

    /** UC11 step 3.1.1.1.1 (self-call) — sets the current level. */
    private void setLevel(String level) {
        this.currentLevel = level;
    }

    public int       getStudentId()                       { return studentId; }
    public void      setStudentId(int v)                  { this.studentId = v; }
    public int       getSkillId()                         { return skillId; }
    public void      setSkillId(int v)                    { this.skillId = v; }
    public String    getCurrentLevel()                    { return currentLevel; }
    public void      setCurrentLevel(String v)            { this.currentLevel = v; }
    public LocalDate getLastAssessmentDate()              { return lastAssessmentDate; }
    public void      setLastAssessmentDate(LocalDate v)   { this.lastAssessmentDate = v; }

    @Override
    public String toString() {
        return "SkillProficiency{student=" + studentId + ", skill=" + skillId
               + ", level='" + currentLevel + "'}";
    }
}

