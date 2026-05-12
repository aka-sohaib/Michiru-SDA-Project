package com.example.michiru.db;

/**
 * Class definition for DatabaseConnection.
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL = "jdbc:mysql://localhost:3306/michiru_db"
                                         + "?useSSL=false"
                                         + "&allowPublicKeyRetrieval=true"
                                         + "&serverTimezone=UTC"
                                         + "&characterEncoding=UTF-8";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    private static volatile DatabaseConnection instance;
    private Connection connection;

    private DatabaseConnection() {
        try {
            this.connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println("[DB] Connected to michiru_db successfully.");
        } catch (SQLException e) {
            System.err.println("[DB] Initial connection failed: " + e.getMessage());
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
            System.out.println("[DB] Connection lost — attempting reconnect…");
            connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println("[DB] Reconnected to michiru_db.");
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
                System.out.println("[DB] Connection closed.");
            } catch (SQLException e) {
                System.err.println("[DB] Error closing connection: " + e.getMessage());
            }
        }
    }
}

