package com.example.michiru.session;

import com.example.michiru.model.User;

/**
 * Application-scoped singleton that holds the currently authenticated
 * {@link User} for the duration of the session.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Set immediately after a successful login:
 *       {@code UserSession.getInstance().setCurrentUser(user);}</li>
 *   <li>Read from any controller that needs the logged-in user's data.</li>
 *   <li>Cleared on logout:
 *       {@code UserSession.getInstance().clearSession();}</li>
 * </ol>
 *
 * <h3>Thread-safety</h3>
 * Uses double-checked locking on the singleton; the {@code currentUser}
 * field is {@code volatile} so writes are visible across the JavaFX
 * Application Thread and any background threads.
 */
public class UserSession {

    // ── Singleton ────────────────────────────────────────────────────────────

    private static volatile UserSession instance;

    private UserSession() {}

    /**
     * Returns the one global {@link UserSession}, creating it on first call.
     *
     * @return the singleton {@code UserSession}
     */
    public static UserSession getInstance() {
        if (instance == null) {
            synchronized (UserSession.class) {
                if (instance == null) {
                    instance = new UserSession();
                }
            }
        }
        return instance;
    }

    // ── Session state ─────────────────────────────────────────────────────────

    /** The currently logged-in user. {@code null} if no one is logged in. */
    private volatile User currentUser;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Stores the authenticated user.  Call this right after a successful
     * {@code loginUser()} response from the DAO.
     *
     * @param user the authenticated {@link User}; must not be {@code null}
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /**
     * Returns the currently logged-in user.
     *
     * @return the {@link User}, or {@code null} if no session is active
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Convenience: returns the role string of the current user, or
     * {@code null} if no session is active.
     *
     * @return one of {@code "STUDENT"}, {@code "MENTOR"},
     *         {@code "INTERNSHIP_COORDINATOR"}, or {@code null}
     */
    public String getCurrentRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    /**
     * Convenience: checks whether a session is currently active.
     *
     * @return {@code true} if a user is logged in
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Clears the session.  Call this when the user logs out.
     */
    public void clearSession() {
        this.currentUser = null;
    }

    @Override
    public String toString() {
        return "UserSession{currentUser=" + currentUser + '}';
    }
}
