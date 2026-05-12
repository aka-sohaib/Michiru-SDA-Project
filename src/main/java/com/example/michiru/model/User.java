package com.example.michiru.model;

/**
 * Class definition for User.
 */

public class User {

    private int userId;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String role;

    /** Creates an empty user for frameworks or tests. */
    public User() {}

    /**
     * Creates a user with all scalar fields populated from the database or registration form.
     */
    public User(int userId, String firstName, String lastName,
                String email, String password, String role) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    /**
     * Factory that splits a single full-name string into first/last name fields.
     *
     * <p>Splitting policy: the first space separates first name from last name.
     * If there is no space, the entire string becomes the first name and last name
     * is empty. This is the <strong>single</strong> place this rule is defined.</p>
     *
     * @param fullName  the combined display name entered by the user
     * @param email     email address
     * @param password  password (plain or hashed)
     * @param role      database role literal
     * @return a new User with userId 0 (not yet persisted)
     */
    public static User fromFullName(String fullName, String email, String password, String role) {
        String first;
        String last;
        int spaceIdx = fullName.indexOf(' ');
        if (spaceIdx > 0) {
            first = fullName.substring(0, spaceIdx).trim();
            last  = fullName.substring(spaceIdx + 1).trim();
        } else {
            first = fullName;
            last  = "";
        }
        return new User(0, first, last, email, password, role);
    }

    /** Returns the primary key from {@code users.user_id}. */
    /**
     * Executes getUserId.
     */
    public int getUserId() {
        return userId;
    }

    /** Returns the first name column. */
    /**
     * Executes getFirstName.
     */
    public String getFirstName() {
        return firstName;
    }

    /** Returns the last name column. */
    /**
     * Executes getLastName.
     */
    public String getLastName() {
        return lastName;
    }

    /** Returns the unique email address. */
    /**
     * Executes getEmail.
     */
    public String getEmail() {
        return email;
    }

    /** Returns the stored password hash string. */
    /**
     * Executes getPassword.
     */
    public String getPassword() {
        return password;
    }

    /** Returns the database role enum literal. */
    /**
     * Executes getRole.
     */
    public String getRole() {
        return role;
    }

    /** Returns first and last name separated by a space for display. */
    /**
     * Executes getFullName.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** Sets the user id field. */
    /**
     * Executes setUserId.
     */
    public void setUserId(int userId) {
        this.userId = userId;
    }

    /** Sets the first name field. */
    /**
     * Executes setFirstName.
     */
    public void setFirstName(String f) {
        this.firstName = f;
    }

    /** Sets the last name field. */
    /**
     * Executes setLastName.
     */
    public void setLastName(String l) {
        this.lastName = l;
    }

    /** Sets the email field. */
    /**
     * Executes setEmail.
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Sets the password hash field. */
    /**
     * Executes setPassword.
     */
    public void setPassword(String pw) {
        this.password = pw;
    }

    /** Sets the role field. */
    /**
     * Executes setRole.
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Returns a redacted string suitable for logging without password material.
     */
    @Override
    /**
     * Executes toString.
     */
    public String toString() {
        return "User{" +
               "userId=" + userId +
               ", name='" + firstName + " " + lastName + '\'' +
               ", email='" + email + '\'' +
               ", role='" + role + '\'' +
               '}';
    }
}

