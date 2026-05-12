package com.example.michiru.model;

/**
 * Class definition for MentorshipActivity.
 */

public class MentorshipActivity {

    // ── From mentorship_requests ──────────────────────────────────────────────
    private int    requestId;
    private String mentorFirstName;
    private String mentorLastName;
    private String message;         // may be null / blank
    private String requestDate;     // formatted string, e.g. "Oct 12, 2026"
    private String requestStatus;   // PENDING | ACCEPTED | DECLINED | CANCELLED
    private int    creditCost;
    private String declineReason;   // may be null; only populated on DECLINED

    // ── From mentorships (LEFT JOIN — may all be null) ────────────────────────
    private Integer mentorshipId;     // null if no mentorship row yet
    private String  mentorshipStatus; // ACTIVE | COMPLETED | CANCELLED — null if none
    private String  startDate;        // formatted string — null if none
    private String  endDate;          // formatted string — null if none

    // ── Constructor ───────────────────────────────────────────────────────────

    public MentorshipActivity(int requestId,
                               String mentorFirstName, String mentorLastName,
                               String message, String requestDate,
                               String requestStatus, int creditCost,
                               String declineReason,
                               Integer mentorshipId, String mentorshipStatus,
                               String startDate, String endDate) {
        this.requestId        = requestId;
        this.mentorFirstName  = mentorFirstName;
        this.mentorLastName   = mentorLastName;
        this.message          = message;
        this.requestDate      = requestDate;
        this.requestStatus    = requestStatus;
        this.creditCost       = creditCost;
        this.declineReason    = declineReason;
        this.mentorshipId     = mentorshipId;
        this.mentorshipStatus = mentorshipStatus;
        this.startDate        = startDate;
        this.endDate          = endDate;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getRequestId()        { return requestId; }
    public String  getMentorFirstName()  { return mentorFirstName; }
    public String  getMentorLastName()   { return mentorLastName; }
    public String  getMessage()          { return message; }
    public String  getRequestDate()      { return requestDate; }
    public String  getRequestStatus()    { return requestStatus; }
    public int     getCreditCost()       { return creditCost; }
    public String  getDeclineReason()    { return declineReason; }
    public Integer getMentorshipId()     { return mentorshipId; }
    public String  getMentorshipStatus() { return mentorshipStatus; }
    public String  getStartDate()        { return startDate; }
    public String  getEndDate()          { return endDate; }

    // ── Display helpers ───────────────────────────────────────────────────────

    /** e.g. {@code "Steve Jobs"} */
    /**
     * Executes getMentorFullName.
     */
    public String getMentorFullName() {
        return mentorFirstName + " " + mentorLastName;
    }

    /**
     * Resolves the best status string for the UI badge.
     * Mentorship table status (ACTIVE/COMPLETED) takes priority over the
     * request status (ACCEPTED) when a mentorship row exists.
     */
    public String getDisplayStatus() {
        if (mentorshipStatus != null && !mentorshipStatus.isBlank()) {
            return mentorshipStatus;
        }
        return requestStatus;
    }

    /** {@code true} when the student sent a non-blank intro message. */
    /**
     * Executes hasMessage.
     */
    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    /** {@code true} when a decline reason is available. */
    /**
     * Executes hasDeclineReason.
     */
    public boolean hasDeclineReason() {
        return "DECLINED".equalsIgnoreCase(requestStatus)
                && declineReason != null && !declineReason.isBlank();
    }

    /** {@code true} when an active mentorship row is linked (ACTIVE or COMPLETED). */
    /**
     * Executes hasMentorshipDates.
     */
    public boolean hasMentorshipDates() {
        return mentorshipId != null && startDate != null;
    }

    @Override
    /**
     * Executes toString.
     */
    public String toString() {
        return "MentorshipActivity{id=" + requestId
               + ", mentor='" + getMentorFullName()
               + "', status='" + getDisplayStatus() + "'}";
    }
}

