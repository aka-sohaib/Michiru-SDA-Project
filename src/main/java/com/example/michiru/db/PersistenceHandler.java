package com.example.michiru.db;

import com.example.michiru.model.User;

/**
 * DAO interface defining the persistence contract for user authentication
 * and registration operations.
 *
 * <p>This interface is the boundary between the JavaFX UI layer and the
 * database.  Controllers depend on this interface — never on a concrete
 * implementation — so the backing store can be swapped without touching
 * any UI code.</p>
 *
 * <p>All implementations must handle their own {@link java.sql.SQLException}s
 * internally and never propagate raw JDBC exceptions to the caller.</p>
 */
public interface PersistenceHandler {

    /**
     * Checks whether the given e-mail address is already registered in
     * the {@code users} table.
     *
     * @param email the e-mail address to look up (case-insensitive)
     * @return {@code true} if a row with that e-mail exists, {@code false}
     *         otherwise (or on DB error, to avoid blocking the UI fatally)
     */
    boolean checkEmailExists(String email);

    /**
     * Attempts to authenticate a user by matching the supplied credentials
     * against the {@code users} table.
     *
     * <p>The password is hashed before comparison — plain-text is never
     * sent to or compared in the database.</p>
     *
     * @param email    the user's registered e-mail address
     * @param password the plain-text password supplied at the login form
     * @return a fully-populated {@link User} object on success,
     *         or {@code null} if credentials are invalid / a DB error occurs
     */
    User loginUser(String email, String password);

    /**
     * Inserts a new user record into the {@code users} table (and the
     * appropriate role sub-table: {@code students} or {@code mentors}).
     *
     * <p>The password stored in the supplied {@link User} must already be
     * in plain-text; hashing is performed by the implementation before
     * the INSERT statement is executed.</p>
     *
     * @param user a {@link User} object populated with firstName, lastName,
     *             email, plain-text password, and role
     * @return a human-readable result string, specifically:
     *         <ul>
     *           <li>{@code "Registration successful!"} — all good</li>
     *           <li>{@code "Email already exists"} — duplicate e-mail</li>
     *           <li>{@code "Database error"} — unexpected SQL failure</li>
     *         </ul>
     */
    String registerUser(User user);
}
