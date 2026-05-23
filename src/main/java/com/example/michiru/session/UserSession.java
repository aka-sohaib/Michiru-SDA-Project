package com.example.michiru.session;

/**
 * Defines the UserSession component in the Michiru application.
 */

import com.example.michiru.model.User;

public class UserSession {

    private static volatile UserSession instance;

    private UserSession() {}

    /**
     * Returns the process-wide session holder, creating it on first use.
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

    private volatile User currentUser;

    /**
     * Stores the user returned from a successful login flow.
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /**
     * Returns the logged-in user, or null when no session exists.
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Returns the raw role string from the current user, or null if logged out.
     */
    public String getCurrentRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    /**
     * Returns true when a non-null user is bound to this session.
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Clears the bound user on logout.
     */
    public void clearSession() {
        this.currentUser = null;
    }

    /**
     * Returns a short diagnostic representation of session state.
     */
    @Override
    public String toString() {
        return "UserSession{currentUser=" + currentUser + '}';
    }
}

