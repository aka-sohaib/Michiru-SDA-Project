package com.example.michiru.model;

// Immutable readiness snapshot + skill gaps for roadmap UI and Groq prompt.

import java.util.List;

public class StudentReadinessDTO {

    private final int                       studentId;
    private final String                    targetField;
    private final double                    readinessScore;
    private final List<ReadinessSkillResult> skillGaps;

    public StudentReadinessDTO(int studentId,
                               String targetField,
                               double readinessScore,
                               List<ReadinessSkillResult> skillGaps) {
        this.studentId      = studentId;
        this.targetField    = targetField;
        this.readinessScore = readinessScore;
        this.skillGaps      = List.copyOf(skillGaps);
    }

    public int                        getStudentId()      { return studentId; }
    public String                     getTargetField()    { return targetField; }
    public double                     getReadinessScore() { return readinessScore; }
    public List<ReadinessSkillResult> getSkillGaps()      { return skillGaps; }

    // MAJOR_GAP and MINOR_GAP only (excludes NO_GAP).
    public List<ReadinessSkillResult> getActualGaps() {
        return skillGaps.stream()
                .filter(r -> !"NO_GAP".equals(r.getGapStatus()))
                .toList();
    }

    @Override
    public String toString() {
        return "StudentReadinessDTO{studentId=" + studentId
                + ", targetField='" + targetField + "'"
                + ", readinessScore=" + String.format("%.1f", readinessScore)
                + ", gaps=" + skillGaps.size() + "}";
    }
}
