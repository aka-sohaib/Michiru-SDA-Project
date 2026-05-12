package com.example.michiru.db;

/**
 * Interface definition for PersistenceHandler.
 */

import com.example.michiru.model.User;

public interface PersistenceHandler {

    /** Returns true when the email already exists in the users table. */
    boolean checkEmailExists(String email);

    /** Authenticates hashed credentials and returns a populated user or null on failure. */
    User loginUser(String email, String password);

    /**
     * Registers a student or mentor user and returns a fixed result token describing success or failure.
     */
    String registerUser(User user);
}

