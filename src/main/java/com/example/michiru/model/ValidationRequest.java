package com.example.michiru.model;

/**
 * Class definition for ValidationRequest.
 */

public class ValidationRequest {

    // ── DB-mapped fields ──────────────────────────────────────────────────────

    private int     validationId;
    private int     studentId;
    private Integer mentorId;        // nullable
    private int     skillId;
    private String  requestedLevel;
    private String  evidenceType;
    private String  note;
    private String  requestDate;
    private String  status;
    private String  mentorFeedback;  // nullable
    private String  resolvedDate;    // nullable

    // ── Transient / display fields ────────────────────────────────────────────

    /** Populated from the {@code skills.name} JOIN — not stored in this table. */
    private String skillName;

    /** Populated from the {@code users} JOIN — not stored in this table. */
    private String studentName;

    // ── Constructors ──────────────────────────────────────────────────────────

    public ValidationRequest() {}

    /** Full constructor for building from a ResultSet JOIN query. */
    public ValidationRequest(int validationId, int studentId, Integer mentorId,
                             int skillId, String skillName,
                             String requestedLevel, String evidenceType,
                             String note, String requestDate,
                             String status, String mentorFeedback,
                             String resolvedDate) {
        this.validationId   = validationId;
        this.studentId      = studentId;
        this.mentorId       = mentorId;
        this.skillId        = skillId;
        this.skillName      = skillName;
        this.requestedLevel = requestedLevel;
        this.evidenceType   = evidenceType;
        this.note           = note;
        this.requestDate    = requestDate;
        this.status         = status;
        this.mentorFeedback = mentorFeedback;
        this.resolvedDate   = resolvedDate;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getValidationId()   { return validationId; }
    public int     getStudentId()      { return studentId; }
    public Integer getMentorId()       { return mentorId; }
    public int     getSkillId()        { return skillId; }
    public String  getSkillName()      { return skillName; }
    public String  getStudentName()    { return studentName; }
    public String  getRequestedLevel() { return requestedLevel; }
    public String  getEvidenceType()   { return evidenceType; }
    public String  getNote()           { return note; }
    public String  getRequestDate()    { return requestDate; }
    public String  getStatus()         { return status; }
    public String  getMentorFeedback() { return mentorFeedback; }
    public String  getResolvedDate()   { return resolvedDate; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setValidationId(int id)             { this.validationId   = id; }
    public void setStudentId(int id)                { this.studentId      = id; }
    public void setMentorId(Integer id)             { this.mentorId       = id; }
    public void setSkillId(int id)                  { this.skillId        = id; }
    public void setSkillName(String name)           { this.skillName      = name; }
    public void setStudentName(String name)         { this.studentName    = name; }
    public void setRequestedLevel(String level)     { this.requestedLevel = level; }
    public void setEvidenceType(String type)        { this.evidenceType   = type; }
    public void setNote(String note)                { this.note           = note; }
    public void setRequestDate(String date)         { this.requestDate    = date; }
    public void setStatus(String status)            { this.status         = status; }
    public void setMentorFeedback(String feedback)  { this.mentorFeedback = feedback; }
    public void setResolvedDate(String date)        { this.resolvedDate   = date; }

    // ── SD-Mandated Methods (UC04, UC11) ─────────────────────────────────────

    /**
     * UC11 step 3.1.1 — delegates proficiency update to the associated
     * {@link SkillProficiency} entity.
     */
    public void updateProficiency(String level) {
        // TODO: delegate to SkillProficiency.updateProficiencyLevel(level)
    }

    /**
     * UC11 step 4.1.1 — marks the request as rejected with mentor feedback.
     */
    public void markRejected(String feedback) {
        this.status         = "REJECTED";
        this.mentorFeedback = feedback;
    }

    /**
     * UC11 (implied) — returns student profile for mentor review.
     */
    public Object getStudentProfile() {
        // TODO: delegate to Student.getProfile() when wired
        return null;
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    /**
     * Returns a formatted date string truncated to {@code yyyy-MM-dd} for
     * display in the history table (drops the time component).
     */
    public String getDisplayDate() {
        if (requestDate == null || requestDate.length() < 10) return "—";
        return requestDate.substring(0, 10);
    }

    /**
     * Returns the evidence URL portion of the combined note field.
     * Convention: URL is stored on the first line; description follows after "\n---\n".
     */
    public String getEvidenceUrl() {
        if (note == null || note.isBlank()) return "";
        int sep = note.indexOf("\n---\n");
        return sep >= 0 ? note.substring(0, sep).trim() : note.trim();
    }

    /**
     * Returns the description portion of the combined note field.
     */
    public String getDescription() {
        if (note == null || note.isBlank()) return "";
        int sep = note.indexOf("\n---\n");
        return sep >= 0 ? note.substring(sep + 5).trim() : "";
    }

    @Override
    /**
     * Executes toString.
     */
    public String toString() {
        return "ValidationRequest{id=" + validationId
               + ", skill='" + skillName + "'"
               + ", level='" + requestedLevel + "'"
               + ", status='" + status + "'"
               + ", date='" + requestDate + "'}";
    }
}

