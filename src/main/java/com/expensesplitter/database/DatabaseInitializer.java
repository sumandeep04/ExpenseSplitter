package com.expensesplitter.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initializes database tables, indexes, and initial settings on startup.
 */
public class DatabaseInitializer {

    public static void initialize() {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create groups table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    category TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
            """);

            // Create members table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT,
                    phone TEXT,
                    created_at TEXT NOT NULL
                );
            """);

            // Create group_members mapping table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS group_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL,
                    member_id INTEGER NOT NULL,
                    joined_at TEXT NOT NULL,
                    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
                    FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE,
                    UNIQUE(group_id, member_id)
                );
            """);

            // Create expenses table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    amount NUMERIC NOT NULL,
                    paid_by_member_id INTEGER NOT NULL,
                    expense_date TEXT NOT NULL,
                    category TEXT NOT NULL,
                    notes TEXT,
                    split_method TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
                    FOREIGN KEY(paid_by_member_id) REFERENCES members(id)
                );
            """);

            // Create expense_splits table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expense_splits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    expense_id INTEGER NOT NULL,
                    member_id INTEGER NOT NULL,
                    share_amount NUMERIC NOT NULL,
                    share_percentage NUMERIC,
                    FOREIGN KEY(expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
                    FOREIGN KEY(member_id) REFERENCES members(id)
                );
            """);

            // Create settlements table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settlements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL,
                    from_member_id INTEGER NOT NULL,
                    to_member_id INTEGER NOT NULL,
                    amount NUMERIC NOT NULL,
                    amount_paid NUMERIC NOT NULL DEFAULT 0,
                    status TEXT NOT NULL,
                    due_date TEXT,
                    settlement_date TEXT,
                    notes TEXT,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
                    FOREIGN KEY(from_member_id) REFERENCES members(id),
                    FOREIGN KEY(to_member_id) REFERENCES members(id)
                );
            """);

            // Create settings table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
            """);

            // Indexes for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_group_members_member ON group_members(member_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_expenses_group ON expenses(group_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_expense_splits_expense ON expense_splits(expense_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_settlements_group ON settlements(group_id);");

            // Default currency if not set
            stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('currency_symbol', '₹');");
            stmt.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('app_initialized', 'true');");

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }
}
