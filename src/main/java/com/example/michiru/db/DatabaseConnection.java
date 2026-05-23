package com.example.michiru.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized MySQL connection manager for the persistence tier.
 */
public class DatabaseConnection {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());

    private static final String URL = "jdbc:mysql://localhost:3306/michiru_db"
                                         + "?useSSL=false"
                                         + "&allowPublicKeyRetrieval=true"
                                         + "&serverTimezone=UTC"
                                         + "&characterEncoding=UTF-8";
    // Academic demo configuration: local XAMPP defaults are kept here for easy evaluator setup.
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    private static volatile DatabaseConnection instance;
    private Connection connection;

    private DatabaseConnection() {
        try {
            this.connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            LOGGER.info("Connected to michiru_db successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Initial database connection failed.", e);
        }
    }

    /**
     * Returns the process-wide connection holder, creating it on first use with double-checked locking.
     */
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Returns the shared singleton connection, reopening it when the previous handle was closed.
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            LOGGER.info("Database connection lost; attempting reconnect.");
            connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            LOGGER.info("Reconnected to michiru_db.");
        }
        return connection;
    }

    /**
     * Opens an isolated connection for manual transactions; callers must close it when finished.
     */
    public Connection getNewConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    /**
     * Closes the shared connection during application shutdown.
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("Database connection closed.");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database connection.", e);
            }
        }
    }
}
