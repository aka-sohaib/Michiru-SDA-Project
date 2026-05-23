package com.example.michiru.model;

/**
 * Defines the MentorProfile component in the Michiru application.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MentorProfile {

    // ── Fields ───────────────────────────────────────────────────────────────

    private int          mentorId;
    private String       firstName;
    private String       lastName;
    private String       bio;
    private int          yearsOfExperience;
    private double       rating;
    private boolean      isAvailable;
    private int          creditCost;
    private List<String> skillNames;

    // ── Constructors ──────────────────────────────────────────────────────────

    public MentorProfile() {
        this.skillNames = new ArrayList<>();
    }

    /**
     * Full constructor used when mapping from a JOIN ResultSet.
     *
     * @param skillNamesCsv  "||"-delimited skill-name string from GROUP_CONCAT,
     *                       or {@code null} / blank if the mentor has no skills.
     */
    public MentorProfile(int mentorId, String firstName, String lastName,
                         String bio, int yearsOfExperience, double rating,
                         boolean isAvailable, int creditCost,
                         String skillNamesCsv) {
        this.mentorId          = mentorId;
        this.firstName         = firstName;
        this.lastName          = lastName;
        this.bio               = bio;
        this.yearsOfExperience = yearsOfExperience;
        this.rating            = rating;
        this.isAvailable       = isAvailable;
        this.creditCost        = creditCost;
        this.skillNames        = parseSkills(skillNamesCsv);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int          getMentorId()          { return mentorId; }
    public String       getFirstName()         { return firstName; }
    public String       getLastName()          { return lastName; }
    public String       getBio()               { return bio; }
    public int          getYearsOfExperience() { return yearsOfExperience; }
    public double       getRating()            { return rating; }
    public boolean      isAvailable()          { return isAvailable; }
    public int          getCreditCost()        { return creditCost; }
    public List<String> getSkillNames()        { return skillNames; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setMentorId(int id)               { this.mentorId          = id; }
    public void setFirstName(String n)            { this.firstName         = n; }
    public void setLastName(String n)             { this.lastName          = n; }
    public void setBio(String bio)                { this.bio               = bio; }
    public void setYearsOfExperience(int y)       { this.yearsOfExperience = y; }
    public void setRating(double r)               { this.rating            = r; }
    public void setAvailable(boolean a)           { this.isAvailable       = a; }
    public void setCreditCost(int c)              { this.creditCost        = c; }
    public void setSkillNames(List<String> names) { this.skillNames        = names; }

    // ── Display helpers ───────────────────────────────────────────────────────

    /** e.g. {@code "John Doe"} */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Two-letter initials for the avatar circle, e.g. {@code "JD"}.
     * Falls back to the first letter of firstName if lastName is blank.
     */
    public String getInitials() {
        String f = (firstName != null && !firstName.isEmpty()) ? firstName.substring(0, 1) : "?";
        String l = (lastName  != null && !lastName.isEmpty())  ? lastName.substring(0, 1)  : "";
        return (f + l).toUpperCase();
    }

    /** e.g. {@code "3 yrs exp"} or {@code "New mentor"} */
    public String getExperienceLabel() {
        if (yearsOfExperience <= 0) return "New mentor";
        return yearsOfExperience == 1 ? "1 yr exp" : yearsOfExperience + " yrs exp";
    }

    /** e.g. {@code "4.5"} — returns {@code "—"} when rating is zero/unset */
    public String getRatingDisplay() {
        return rating <= 0 ? "—" : String.format("%.1f", rating);
    }

    // ── Rating-tier helpers ───────────────────────────────────────────────────

    /**
     * Returns the CSS class to add to the mentor card VBox for a rating-based
     * border colour and drop-shadow glow (mirrors the skill hub-card pattern).
     * e.g. {@code "hub-card-expert"} for a 4.9-rated mentor.
     */
    public String getRatingCardClass() {
        if (rating > 4.8) return "hub-card-expert";
        if (rating > 4.0) return "hub-card-advanced";
        if (rating > 3.0) return "hub-card-intermediate";
        if (rating > 2.0) return "hub-card-beginner";
        return "hub-card-novice";
    }

    /**
     * Returns a human-readable tier label for use in the profile modal badge,
     * e.g. {@code "Expert"}.  Returns {@code null} for unrated / novice mentors.
     */
    public String getRatingTierLabel() {
        if (rating > 4.8) return "Expert";
        if (rating > 4.0) return "Advanced";
        if (rating > 3.0) return "Intermediate";
        if (rating > 2.0) return "Beginner";
        return null;
    }

    /**
     * Returns the CSS modifier class for the {@code exam-tier-badge} in the modal,
     * e.g. {@code "exam-tier-badge-expert"}.  Returns {@code null} for novice tier.
     */
    public String getRatingBadgeClass() {
        if (rating > 4.8) return "exam-tier-badge-expert";
        if (rating > 4.0) return "exam-tier-badge-advanced";
        if (rating > 3.0) return "exam-tier-badge-intermediate";
        if (rating > 2.0) return "exam-tier-badge-beginner";
        return null;
    }

    // ── Filtering helper ──────────────────────────────────────────────────────

    /** {@code true} when this mentor teaches the given skill (case-insensitive). */
    public boolean teachesSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) return true;
        String lower = skillName.toLowerCase();
        return skillNames.stream().anyMatch(s -> s.toLowerCase().contains(lower));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static List<String> parseSkills(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(csv.split("\\|\\|")));
    }

    @Override
    public String toString() {
        return "MentorProfile{id=" + mentorId + ", name='" + getFullName()
               + "', rating=" + rating + ", available=" + isAvailable + "}";
    }
}

