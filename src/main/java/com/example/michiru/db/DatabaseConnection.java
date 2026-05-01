package com.example.michiru.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton that vends a single shared {@link Connection} to the
 * {@code michiru_db} MySQL database running on XAMPP (localhost:3306).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   Connection conn = DatabaseConnection.getInstance().getConnection();
 * }</pre>
 *
 * <h3>Thread-safety</h3>
 * The instance is created with <em>double-checked locking</em> and a
 * {@code volatile} field so it is safe for concurrent access during
 * startup.  For this desktop app a single shared connection is
 * sufficient; no connection pool is needed.
 *
 * <h3>Reconnect logic</h3>
 * {@link #getConnection()} checks whether the underlying connection is
 * still alive ({@link Connection#isClosed()}) and re-opens it
 * transparently — essential if XAMPP is restarted mid-session.
 */
public class DatabaseConnection {

    // ── JDBC constants ────────────────────────────────────────────────────

    private static final String URL      = "jdbc:mysql://localhost:3306/michiru_db"
                                         + "?useSSL=false"
                                         + "&allowPublicKeyRetrieval=true"
                                         + "&serverTimezone=UTC"
                                         + "&characterEncoding=UTF-8";
    private static final String USERNAME = "root";   // XAMPP default
    private static final String PASSWORD = "";        // XAMPP default (no password)

    // ── Singleton state ────────────────────────────────────────────────────

    private static volatile DatabaseConnection instance;
    private Connection connection;

    // ── Private constructor ─────────────────────────────────────────────────

    private DatabaseConnection() {
        try {
            this.connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println("[DB] Connected to michiru_db successfully.");
        } catch (SQLException e) {
            System.err.println("[DB] Initial connection failed: " + e.getMessage());
            // connection stays null; callers must handle a null/closed connection.
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the singleton instance, creating it on first call
     * (double-checked locking, thread-safe).
     *
     * @return the {@code DatabaseConnection} singleton
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
     * Returns a live {@link Connection}, reconnecting if the previous one
     * was closed (e.g. XAMPP restart, idle timeout).
     *
     * @return an open {@link Connection} to {@code michiru_db}
     * @throws SQLException if a new connection cannot be established
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
     * Returns a <b>brand-new, isolated</b> {@link Connection} for operations
     * that require their own transaction scope (e.g. {@code setAutoCommit(false)}).
     *
     * <p>The caller <b>must</b> close this connection (use try-with-resources)
     * to prevent connection leaks.  This connection is completely independent
     * of the shared singleton returned by {@link #getConnection()}.</p>
     *
     * @return a new, independent {@link Connection} to {@code michiru_db}
     * @throws SQLException if a connection cannot be established
     */
    public Connection getNewConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    /**
     * Gracefully closes the underlying connection.
     * Call this when the JavaFX application shuts down
     * (e.g. from {@code Application.stop()}).
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
