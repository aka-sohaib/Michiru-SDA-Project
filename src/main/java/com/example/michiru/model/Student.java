package com.example.michiru.model;

/**
 * Defines the Student component in the Michiru application.
 */

import java.time.LocalDate;

public class Student {

    private int studentId;
    private int userId;
    private String firstName;
    private String lastName;
    private String email;
    private String passwordHash;
    private int creditBalance;
    private LocalDate registrationDate;
    private String targetField;

    /** Creates an empty student for binding or tests. */
    public Student() {}

    /**
     * Creates a student with identifiers, contact fields, and starting credit balance.
     */
    public Student(int studentId, int userId, String firstName, String lastName,
                   String email, int creditBalance) {
        this.studentId = studentId;
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.creditBalance = creditBalance;
    }

    /**
     * Builds a new {@link Assessment} instance for the given skill.
     */
    public Assessment createAssessment(Skill targetSkill) {
        return new Assessment(this.studentId, targetSkill.getSkillId());
    }

    /**
     * Marks the in-memory assessment as cancelled without persisting it.
     */
    public void abortAssessment(Assessment assessment) {
        if (assessment != null) {
            assessment.markCancelled();
        }
    }

    /**
     * Applies a passed assessment outcome by updating proficiency, awarding credits, and invalidating readiness cache.
     */
    public void recordAssessmentResult(Skill skill, double score, String level) {
        updateSkillProficiency(skill, level);
        addCredits(10);
        invalidateCachedReadiness();
    }

    /**
     * Returns true when the current balance covers the requested cost.
     */
    public boolean checkCreditBalance(int cost) {
        return creditBalance >= cost;
    }

    /**
     * Alias for {@link #checkCreditBalance(int)} used by roadmap flows.
     */
    public boolean checkSufficientCredits(int cost) {
        return checkCreditBalance(cost);
    }

    /**
     * Subtracts credits from the in-memory balance.
     */
    public void deductCredits(int cost) {
        this.creditBalance -= cost;
    }

    /**
     * Adds credits to the in-memory balance after successful activities.
     */
    public void addCredits(int amount) {
        this.creditBalance += amount;
    }

    /**
     * Placeholder factory for a mentorship request until persistence wiring is complete.
     */
    public MentorshipRequest createMentorshipRequest(String message,
                                                     int mentorId,
                                                     int cost) {
        return null;
    }

    /**
     * Placeholder for a future readiness DTO backed by the database.
     */
    public Object getReadinessProfile() {
        return null;
    }

    /**
     * Placeholder for aggregated dashboard data for mentor views.
     */
    public Object getOverviewData() {
        return null;
    }

    /**
     * Placeholder for mentor-facing profile projection.
     */
    public Object getProfile() {
        return null;
    }

    /**
     * Placeholder hook for associating a computed readiness report with this student.
     */
    public void saveReport(ReadinessReport report) {
    }

    private void updateSkillProficiency(Skill skill, String level) {
    }

    private void invalidateCachedReadiness() {
    }

    /** Returns the student primary key. */
    public int getStudentId() {
        return studentId;
    }

    /** Sets the student primary key. */
    public void setStudentId(int v) {
        this.studentId = v;
    }

    /** Returns the linked user id. */
    public int getUserId() {
        return userId;
    }

    /** Sets the linked user id. */
    public void setUserId(int v) {
        this.userId = v;
    }

    /** Returns the first name. */
    public String getFirstName() {
        return firstName;
    }

    /** Sets the first name. */
    public void setFirstName(String v) {
        this.firstName = v;
    }

    /** Returns the last name. */
    public String getLastName() {
        return lastName;
    }

    /** Sets the last name. */
    public void setLastName(String v) {
        this.lastName = v;
    }

    /** Returns the email address. */
    public String getEmail() {
        return email;
    }

    /** Sets the email address. */
    public void setEmail(String v) {
        this.email = v;
    }

    /** Returns the current credit balance. */
    public int getCreditBalance() {
        return creditBalance;
    }

    /** Sets the credit balance. */
    public void setCreditBalance(int v) {
        this.creditBalance = v;
    }

    /** Returns the registration date when populated. */
    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    /** Sets the registration date. */
    public void setRegistrationDate(LocalDate v) {
        this.registrationDate = v;
    }

    /** Returns the student's target career field. */
    public String getTargetField() {
        return targetField;
    }

    /** Sets the target career field. */
    public void setTargetField(String v) {
        this.targetField = v;
    }

    /** Returns the display name composed from first and last name. */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Returns a compact summary string for debugging.
     */
    @Override
    public String toString() {
        return "Student{id=" + studentId + ", name='" + getFullName()
               + "', credits=" + creditBalance + "}";
    }
}

