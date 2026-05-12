package com.example.michiru.model;

/**
 * Class definition for MentorshipRequest.
 */

import java.util.ArrayList;
import java.util.List;

public class MentorshipRequest {

    // ── Inner record: one skill proficiency tag ───────────────────────────────

    /**
     * Lightweight pair of (skill name, highest achieved level) used to render
     * proficiency-level pills inside the review modal.
     *
     * @param skillName  e.g. "Java", "Python"
     * @param level      one of NOVICE | BEGINNER | INTERMEDIATE | ADVANCED | EXPERT
     */
    public record SkillTag(String skillName, String level) {

        /**
         * Returns the {@code exam-tier-badge-*} CSS class for this level,
         * or an empty string for NOVICE.
         */
        public String getBadgeCssClass() {
            return switch (level.toUpperCase()) {
                case "EXPERT"        -> "exam-tier-badge-expert";
                case "ADVANCED"      -> "exam-tier-badge-advanced";
                case "INTERMEDIATE"  -> "exam-tier-badge-intermediate";
                case "BEGINNER"      -> "exam-tier-badge-beginner";
                default              -> "";
            };
        }

        /** Short display label (capitalised first letter). */
        /**
         * Executes getLevelLabel.
         */
        public String getLevelLabel() {
            if (level == null || level.isEmpty()) return "Novice";
            return level.charAt(0) + level.substring(1).toLowerCase();
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────

    private final int    requestId;
    private final int    studentId;
    private final String studentFirstName;
    private final String studentLastName;
    private final String message;        // may be null / blank
    private final String requestDate;    // DB-formatted, e.g. "Apr 29, 2026"
    private final int    creditCost;

    /** Populated by a second fetch after the base request list is loaded. */
    private List<SkillTag> skillTags = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public MentorshipRequest(int    requestId,
                             int    studentId,
                             String studentFirstName,
                             String studentLastName,
                             String message,
                             String requestDate,
                             int    creditCost) {
        this.requestId        = requestId;
        this.studentId        = studentId;
        this.studentFirstName = studentFirstName;
        this.studentLastName  = studentLastName;
        this.message          = message;
        this.requestDate      = requestDate;
        this.creditCost       = creditCost;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int            getRequestId()        { return requestId; }
    public int            getStudentId()        { return studentId; }
    public String         getStudentFirstName() { return studentFirstName; }
    public String         getStudentLastName()  { return studentLastName; }
    public String         getMessage()          { return message; }
    public String         getRequestDate()      { return requestDate; }
    public int            getCreditCost()       { return creditCost; }
    public List<SkillTag> getSkillTags()        { return skillTags; }

    /**
     * Executes setSkillTags.
     */
    public void setSkillTags(List<SkillTag> tags) {
        this.skillTags = tags != null ? tags : new ArrayList<>();
    }

    // ── SD-Mandated Methods (UC08, UC09) ─────────────────────────────────────

    /** Mutable status field for SD method support. */
    private String status = "PENDING";
    /** Decline reason — populated by {@link #markDeclined}. */
    private String declineReason;

    /**
     * UC08 step 4.1.2.1 — sets the request status and date.
     */
    public void setDetails(String status, String date) {
        this.status = status;
        // requestDate is final from constructor; this is for new requests
    }

    /**
     * UC09 step 2.1.2 — delegates to associated Student's getProfile().
     */
    public Object getStudentProfile() {
        // TODO: delegate to Student.getProfile() when wired
        return null;
    }

    /**
     * UC09 step 3.1.2 — marks request as accepted.
     */
    public void markAccepted() {
        this.status = "ACCEPTED";
    }

    /**
     * UC09 step 4.1.1 — marks request as declined with a reason.
     */
    public void markDeclined(String reason) {
        this.status        = "DECLINED";
        this.declineReason = reason;
    }

    public String getRequestStatus()  { return status; }
    public String getDeclineReason()  { return declineReason; }

    // ── Display helpers ───────────────────────────────────────────────────────

    /** e.g. {@code "Jane Doe"} */
    /**
     * Executes getFullName.
     */
    public String getFullName() {
        return studentFirstName + " " + studentLastName;
    }

    /**
     * Two-letter initials for the avatar circle, e.g. {@code "JD"}.
     * Falls back to the first letter of firstName if lastName is blank.
     */
    public String getInitials() {
        String f = (studentFirstName != null && !studentFirstName.isEmpty())
                   ? studentFirstName.substring(0, 1) : "?";
        String l = (studentLastName  != null && !studentLastName.isEmpty())
                   ? studentLastName.substring(0, 1)  : "";
        return (f + l).toUpperCase();
    }

    /** {@code true} when the student included a non-blank intro message. */
    /**
     * Executes hasMessage.
     */
    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    @Override
    /**
     * Executes toString.
     */
    public String toString() {
        return "MentorshipRequest{requestId=" + requestId
               + ", student='" + getFullName() + "'}";
    }
}

