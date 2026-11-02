package com.expensesplitter.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite database connections and connection lifecycle.
 * Guarantees foreign key enforcement on every connection.
 */
public class Database {

    private static String customJdbcUrl = null;

    private Database() {}

    /**
     * Allows tests to supply an in-memory or custom database URL.
     */
    public static synchronized void setCustomJdbcUrl(String jdbcUrl) {
        customJdbcUrl = jdbcUrl;
    }

    /**
     * Resolves the default database path in the user's home directory.
     */
    public static String getDefaultDatabasePath() {
        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, ".expensesplitter");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        return new File(appDir, "expensesplitter.db").getAbsolutePath();
    }

    /**
     * Obtains the JDBC connection URL.
     */
    public static synchronized String getJdbcUrl() {
        if (customJdbcUrl != null) {
            return customJdbcUrl;
        }
        return "jdbc:sqlite:" + getDefaultDatabasePath();
    }

    /**
     * Gets a new database connection with SQLite foreign keys enabled.
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(getJdbcUrl());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }
}
