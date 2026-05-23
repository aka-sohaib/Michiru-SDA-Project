package com.example.michiru.model;

/**
 * Defines the ReadinessReport component in the Michiru application.
 */

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReadinessReport {

    private int           reportId;
    private int           studentId;
    private int           templateId;
    private double        overallScore;
    private LocalDateTime generatedDate;
    private String        status;

    /** Skill-level results computed by {@link #evaluate}. */
    private List<ReadinessSkillResult> results = new ArrayList<>();

    // ── Constructors ─────────────────────────────────────────────────────────

    public ReadinessReport() {}

    public ReadinessReport(int studentId, int templateId) {
        this.studentId     = studentId;
        this.templateId    = templateId;
        this.generatedDate = LocalDateTime.now();
        this.status        = "DRAFT";
    }

    // ── Business Logic (SD-mandated) ─────────────────────────────────────────

    /**
     * UC07 — evaluates readiness by comparing student proficiencies against
     * the template's skill requirements.
     *
     * <p>Populates {@link #results} and computes {@link #overallScore}.
     * After this call the report is ready for persistence.</p>
     *
     * @param requirements  active skill requirements for the template
     * @param proficiencies map of skillId → student's highest proficiency level
     */
    public void evaluate(List<SkillAssignment> requirements,
                         Map<Integer, String> proficiencies) {
        results.clear();
        double weightedSum = 0.0;
        int    totalWeight = 0;

        for (SkillAssignment req : requirements) {
            if (!"ACTIVE".equals(req.getStatus())) continue;

            String studentLevel  = proficiencies.getOrDefault(req.getSkillId(), "NOVICE");
            String requiredLevel = req.getMinimumProficiencyLevel();

            int studentPts  = levelToPoints(studentLevel);
            int requiredPts = levelToPoints(requiredLevel);

            // Cap at 1.0; NOVICE requirement (0 pts) is always met
            double skillScore = (requiredPts == 0)
                    ? 1.0
                    : Math.min((double) studentPts / requiredPts, 1.0);

            int diff = requiredPts - studentPts;
            String gapStatus = diff <= 0 ? "NO_GAP"
                             : diff == 1 ? "MINOR_GAP"
                                         : "MAJOR_GAP";

            weightedSum += skillScore * req.getWeight();
            totalWeight += req.getWeight();

            results.add(new ReadinessSkillResult(
                    req.getSkillId(), req.getSkillName(), req.getSkillCategory(),
                    studentLevel, requiredLevel,
                    req.getWeight(), skillScore, gapStatus));
        }

        this.overallScore = totalWeight > 0 ? (weightedSum / totalWeight) * 100.0 : 0.0;
        this.status       = "FINALIZED";
    }

    // ── Readiness verdict policy ─────────────────────────────────────────

    /**
     * Threshold-based interpretation of an overall readiness score.
     *
     * <p>The thresholds are business policy owned by this model class
     * (Information Expert): the UI consumes the verdict label and may map
     * it to CSS classes or colours, but must <strong>not</strong> re-implement
     * the threshold logic.</p>
     */
    public enum ReadinessVerdict {

        READY        ("Ready",        80),
        ALMOST_READY ("Almost Ready", 60),
        NEEDS_WORK   ("Needs Work",   40),
        NOT_READY    ("Not Ready",     0);

        private final String displayLabel;
        private final int    minScore;

        ReadinessVerdict(String displayLabel, int minScore) {
            this.displayLabel = displayLabel;
            this.minScore     = minScore;
        }

        /** Human-readable verdict text. */
        public String getDisplayLabel() { return displayLabel; }

        /** Minimum score (inclusive) for this verdict level. */
        public int getMinScore() { return minScore; }

        /**
         * Resolves a numeric score to the matching verdict.
         *
         * @param score overall readiness percentage (0–100)
         * @return the highest verdict whose threshold the score meets
         */
        public static ReadinessVerdict fromScore(double score) {
            for (ReadinessVerdict v : values()) {
                if (score >= v.minScore) return v;
            }
            return NOT_READY;
        }
    }

    /**
     * Returns the policy-based verdict for this report's overall score.
     *
     * @throws IllegalStateException if called before {@link #evaluate}
     */
    public ReadinessVerdict getVerdict() {
        return ReadinessVerdict.fromScore(overallScore);
    }

    /**
     * Returns only the skill results that have a gap (MINOR_GAP or MAJOR_GAP).
     */
    public List<ReadinessSkillResult> getGaps() {
        return results.stream()
                .filter(r -> !"NO_GAP".equals(r.getGapStatus()))
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Maps a proficiency level ENUM string to an integer point value. */
    private static int levelToPoints(String level) {
        return switch (level) {
            case "NOVICE"        -> 0;
            case "BEGINNER"      -> 1;
            case "INTERMEDIATE"  -> 2;
            case "ADVANCED"      -> 3;
            case "EXPERT"        -> 4;
            default              -> 0;
        };
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int                       getReportId()                      { return reportId; }
    public void                      setReportId(int v)                 { this.reportId = v; }
    public int                       getStudentId()                     { return studentId; }
    public void                      setStudentId(int v)                { this.studentId = v; }
    public int                       getTemplateId()                    { return templateId; }
    public void                      setTemplateId(int v)               { this.templateId = v; }
    public double                    getOverallScore()                  { return overallScore; }
    public void                      setOverallScore(double v)          { this.overallScore = v; }
    public LocalDateTime             getGeneratedDate()                 { return generatedDate; }
    public void                      setGeneratedDate(LocalDateTime v)  { this.generatedDate = v; }
    public String                    getStatus()                        { return status; }
    public void                      setStatus(String v)                { this.status = v; }
    public List<ReadinessSkillResult> getResults()                      { return results; }

    @Override
    public String toString() {
        return "ReadinessReport{id=" + reportId + ", score=" + overallScore
               + ", skills=" + results.size() + "}";
    }
}

