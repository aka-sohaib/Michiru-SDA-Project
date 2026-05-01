package com.example.michiru.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Display model for a mentor in the MentorSearchView directory.
 *
 * <p>Combines data from three tables in one query:</p>
 * <pre>
 *  users   : user_id, first_name, last_name
 *  mentors : bio, years_of_experience, rating, is_available, credit_cost
 *  skills  : name (via mentor_expertise_skills JOIN, GROUP_CONCAT'd as "||"-delimited string)
 * </pre>
 *
 * <p>{@code skillNames} is populated by splitting the {@code GROUP_CONCAT} result.
 * It is never null — an empty list means the mentor has no linked skills yet.</p>
 */
public class MentorCard {

    // ── Fields ──────────────────────────────────────────────────────────────

    private int          mentorId;
    private String       firstName;
    private String       lastName;
    private String       bio;
    private int          yearsOfExperience;
    private double       rating;
    private boolean      isAvailable;
    private int          creditCost;
    private List<String> skillNames;

    // ── Constructors ─────────────────────────────────────────────────────────

    public MentorCard() {
        this.skillNames = new ArrayList<>();
    }

    /**
     * Full constructor used when mapping from a JOIN ResultSet.
     *
     * @param skillNamesCsv  "||"-delimited skill name string from GROUP_CONCAT,
     *                        or {@code null} / blank if the mentor has no skills.
     */
    public MentorCard(int mentorId, String firstName, String lastName,
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

    public void setMentorId(int id)                      { this.mentorId          = id; }
    public void setFirstName(String n)                   { this.firstName         = n; }
    public void setLastName(String n)                    { this.lastName          = n; }
    public void setBio(String bio)                       { this.bio               = bio; }
    public void setYearsOfExperience(int y)              { this.yearsOfExperience = y; }
    public void setRating(double r)                      { this.rating            = r; }
    public void setAvailable(boolean a)                  { this.isAvailable       = a; }
    public void setCreditCost(int c)                     { this.creditCost        = c; }
    public void setSkillNames(List<String> names)        { this.skillNames        = names; }

    // ── Convenience ───────────────────────────────────────────────────────────

    /** {@code "John Doe"} */
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

    /**
     * Returns a concise experience label, e.g. {@code "3 yrs"} or {@code "1 yr"}.
     */
    public String getExperienceLabel() {
        if (yearsOfExperience <= 0) return "New mentor";
        return yearsOfExperience == 1 ? "1 yr exp" : yearsOfExperience + " yrs exp";
    }

    /**
     * Returns a display string for the rating, e.g. {@code "4.5"} or {@code "—"}.
     */
    public String getRatingDisplay() {
        return rating <= 0 ? "—" : String.format("%.1f", rating);
    }

    /**
     * Returns {@code true} if this mentor teaches the given skill (case-insensitive).
     */
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
        return "MentorCard{id=" + mentorId + ", name='" + getFullName()
               + "', rating=" + rating + ", available=" + isAvailable + "}";
    }
}
