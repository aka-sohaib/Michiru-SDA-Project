package com.example.michiru.model;

/**
 * Defines the Mentor component in the Michiru application.
 */

public class Mentor {

    private int    mentorId;
    private int    userId;
    private String firstName;
    private String lastName;
    private String email;
    private String passwordHash;
    private String expertise;
    private int    creditCost;

    // ── Constructors ─────────────────────────────────────────────────────────

    public Mentor() {}

    public Mentor(int mentorId, int userId, String firstName, String lastName,
                  String email, String expertise, int creditCost) {
        this.mentorId   = mentorId;
        this.userId     = userId;
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email;
        this.expertise  = expertise;
        this.creditCost = creditCost;
    }

    // ── SD Methods ───────────────────────────────────────────────────────────

    /**
     * UC11 (postcondition) — dispatches a notification about a pending
     * validation request.
     */
    public void notifyValidationRequest(int requestId) {
        // Notification dispatch is outside this JavaFX prototype's persistence-backed scope.
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int    getMentorId()            { return mentorId; }
    public void   setMentorId(int v)       { this.mentorId = v; }
    public int    getUserId()              { return userId; }
    public void   setUserId(int v)         { this.userId = v; }
    public String getFirstName()           { return firstName; }
    public void   setFirstName(String v)   { this.firstName = v; }
    public String getLastName()            { return lastName; }
    public void   setLastName(String v)    { this.lastName = v; }
    public String getEmail()               { return email; }
    public void   setEmail(String v)       { this.email = v; }
    public String getExpertise()           { return expertise; }
    public void   setExpertise(String v)   { this.expertise = v; }
    public int    getCreditCost()          { return creditCost; }
    public void   setCreditCost(int v)     { this.creditCost = v; }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return "Mentor{id=" + mentorId + ", name='" + getFullName()
               + "', expertise='" + expertise + "'}";
    }
}

