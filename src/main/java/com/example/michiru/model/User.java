package com.example.michiru.model;

/**
 * Domain model representing a row in the {@code users} table.
 *
 * <p>Column mapping (snake_case → camelCase):</p>
 * <pre>
 *  user_id    → userId     (INT UNSIGNED  AUTO_INCREMENT)
 *  first_name → firstName  (VARCHAR 100)
 *  last_name  → lastName   (VARCHAR 100)
 *  email      → email      (VARCHAR 255, UNIQUE)
 *  password   → password   (VARCHAR 255, stored as hash)
 *  role       → role       (ENUM: STUDENT | MENTOR | INTERNSHIP_COORDINATOR)
 * </pre>
 *
 * <p>The {@code created_at} timestamp is managed by the DB default and is
 * intentionally omitted from this lightweight model.</p>
 */
public class User {

    // ── Fields ──────────────────────────────────────────────────────────────

    private int userId;
    private String firstName;
    private String lastName;
    private String email;

    /**
     * Stored as a SHA-256 hex-digest. Never expose plain-text passwords here.
     */
    private String password;

    /**
     * One of the exact enum literals used in the DB:
     * {@code STUDENT}, {@code MENTOR}, {@code INTERNSHIP_COORDINATOR}.
     */
    private String role;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** No-arg constructor required for some frameworks / testing. */
    public User() {}

    /**
     * Full constructor — used when building a User from a ResultSet (login)
     * or before inserting a new row (register).
     *
     * @param userId    DB-assigned primary key (0 for a new, unsaved user)
     * @param firstName user's first name
     * @param lastName  user's last name
     * @param email     unique email address
     * @param password  already-hashed password string
     * @param role      exact DB enum string
     */
    public User(int userId, String firstName, String lastName,
                String email, String password, String role) {
        this.userId    = userId;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.email     = email;
        this.password  = password;
        this.role      = role;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int    getUserId()    { return userId; }
    public String getFirstName() { return firstName; }
    public String getLastName()  { return lastName; }
    public String getEmail()     { return email; }
    public String getPassword()  { return password; }
    public String getRole()      { return role; }

    /** Convenience: {@code "John Doe"} — useful for UI display. */
    public String getFullName()  { return firstName + " " + lastName; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setUserId(int userId)       { this.userId = userId; }
    public void setFirstName(String f)      { this.firstName = f; }
    public void setLastName(String l)       { this.lastName = l; }
    public void setEmail(String email)      { this.email = email; }
    public void setPassword(String pw)      { this.password = pw; }
    public void setRole(String role)        { this.role = role; }

    // ── Object overrides ───────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "User{" +
               "userId=" + userId +
               ", name='" + firstName + " " + lastName + '\'' +
               ", email='" + email + '\'' +
               ", role='" + role + '\'' +
               '}';
    }
}
