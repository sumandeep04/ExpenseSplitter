package com.expensesplitter.dao;

import com.expensesplitter.database.Database;
import com.expensesplitter.model.Expense;
import com.expensesplitter.model.ExpenseCategory;
import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.SplitMethod;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Expenses and their participant splits.
 * Uses strict database transactions for atomic financial modifications.
 */
public class ExpenseDAO {

    public Expense create(Expense expense) {
        String insertExpenseSql = """
            INSERT INTO expenses (group_id, description, amount, paid_by_member_id, 
                                  expense_date, category, notes, split_method, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        String insertSplitSql = """
            INSERT INTO expense_splits (expense_id, member_id, share_amount, share_percentage)
            VALUES (?, ?, ?, ?);
        """;

        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false); // Begin transaction

            try (PreparedStatement pstmt = conn.prepareStatement(insertExpenseSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, expense.getGroupId());
                pstmt.setString(2, expense.getDescription().trim());
                pstmt.setBigDecimal(3, expense.getAmount());
                pstmt.setInt(4, expense.getPaidByMemberId());
                pstmt.setString(5, expense.getExpenseDate().toString());
                pstmt.setString(6, expense.getCategory().name());
                pstmt.setString(7, expense.getNotes() != null ? expense.getNotes().trim() : null);
                pstmt.setString(8, expense.getSplitMethod().name());
                LocalDateTime now = LocalDateTime.now();
                pstmt.setString(9, now.toString());
                pstmt.setString(10, now.toString());

                int affected = pstmt.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("Creating expense failed, no rows affected.");
                }

                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        expense.setId(keys.getInt(1));
                    } else {
                        throw new SQLException("Creating expense failed, no ID obtained.");
                    }
                }
            }

            // Insert splits
            try (PreparedStatement splitStmt = conn.prepareStatement(insertSplitSql, Statement.RETURN_GENERATED_KEYS)) {
                for (ExpenseSplit split : expense.getSplits()) {
                    splitStmt.setInt(1, expense.getId());
                    splitStmt.setInt(2, split.getMemberId());
                    splitStmt.setBigDecimal(3, split.getShareAmount());
                    if (split.getSharePercentage() != null) {
                        splitStmt.setBigDecimal(4, split.getSharePercentage());
                    } else {
                        splitStmt.setNull(4, Types.NUMERIC);
                    }
                    splitStmt.executeUpdate();
                    try (ResultSet splitKeys = splitStmt.getGeneratedKeys()) {
                        if (splitKeys.next()) {
                            split.setId(splitKeys.getInt(1));
                        }
                    }
                    split.setExpenseId(expense.getId());
                }
            }

            // Update parent group updated_at
            try (PreparedStatement groupStmt = conn.prepareStatement("UPDATE groups SET updated_at = ? WHERE id = ?;")) {
                groupStmt.setString(1, LocalDateTime.now().toString());
                groupStmt.setInt(2, expense.getGroupId());
                groupStmt.executeUpdate();
            }

            conn.commit(); // Commit transaction
            return expense;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Rollback failed: " + ex.getMessage());
                }
            }
            throw new RuntimeException("Failed to save expense and splits: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    public void update(Expense expense) {
        String updateExpenseSql = """
            UPDATE expenses 
            SET group_id = ?, description = ?, amount = ?, paid_by_member_id = ?,
                expense_date = ?, category = ?, notes = ?, split_method = ?, updated_at = ?
            WHERE id = ?;
        """;

        String deleteSplitsSql = "DELETE FROM expense_splits WHERE expense_id = ?;";

        String insertSplitSql = """
            INSERT INTO expense_splits (expense_id, member_id, share_amount, share_percentage)
            VALUES (?, ?, ?, ?);
        """;

        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(updateExpenseSql)) {
                pstmt.setInt(1, expense.getGroupId());
                pstmt.setString(2, expense.getDescription().trim());
                pstmt.setBigDecimal(3, expense.getAmount());
                pstmt.setInt(4, expense.getPaidByMemberId());
                pstmt.setString(5, expense.getExpenseDate().toString());
                pstmt.setString(6, expense.getCategory().name());
                pstmt.setString(7, expense.getNotes() != null ? expense.getNotes().trim() : null);
                pstmt.setString(8, expense.getSplitMethod().name());
                pstmt.setString(9, LocalDateTime.now().toString());
                pstmt.setInt(10, expense.getId());

                pstmt.executeUpdate();
            }

            // Delete old splits
            try (PreparedStatement delStmt = conn.prepareStatement(deleteSplitsSql)) {
                delStmt.setInt(1, expense.getId());
                delStmt.executeUpdate();
            }

            // Insert updated splits
            try (PreparedStatement splitStmt = conn.prepareStatement(insertSplitSql)) {
                for (ExpenseSplit split : expense.getSplits()) {
                    splitStmt.setInt(1, expense.getId());
                    splitStmt.setInt(2, split.getMemberId());
                    splitStmt.setBigDecimal(3, split.getShareAmount());
                    if (split.getSharePercentage() != null) {
                        splitStmt.setBigDecimal(4, split.getSharePercentage());
                    } else {
                        splitStmt.setNull(4, Types.NUMERIC);
                    }
                    splitStmt.executeUpdate();
                }
            }

            // Update parent group updated_at
            try (PreparedStatement groupStmt = conn.prepareStatement("UPDATE groups SET updated_at = ? WHERE id = ?;")) {
                groupStmt.setString(1, LocalDateTime.now().toString());
                groupStmt.setInt(2, expense.getGroupId());
                groupStmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Rollback failed: " + ex.getMessage());
                }
            }
            throw new RuntimeException("Failed to update expense: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM expenses WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete expense: " + e.getMessage(), e);
        }
    }

    public Expense findById(int id) {
        String sql = """
            SELECT e.*, g.name AS group_name, m.name AS paid_by_name
            FROM expenses e
            JOIN groups g ON e.group_id = g.id
            JOIN members m ON e.paid_by_member_id = m.id
            WHERE e.id = ?;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Expense e = mapRow(rs);
                    e.setSplits(findSplitsByExpenseId(e.getId(), conn));
                    return e;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find expense by ID: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Expense> findByGroupId(int groupId) {
        List<Expense> list = new ArrayList<>();
        String sql = """
            SELECT e.*, g.name AS group_name, m.name AS paid_by_name
            FROM expenses e
            JOIN groups g ON e.group_id = g.id
            JOIN members m ON e.paid_by_member_id = m.id
            WHERE e.group_id = ?
            ORDER BY e.expense_date DESC, e.id DESC;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Expense e = mapRow(rs);
                    e.setSplits(findSplitsByExpenseId(e.getId(), conn));
                    list.add(e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find expenses by group: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Expense> findAll() {
        List<Expense> list = new ArrayList<>();
        String sql = """
            SELECT e.*, g.name AS group_name, m.name AS paid_by_name
            FROM expenses e
            JOIN groups g ON e.group_id = g.id
            JOIN members m ON e.paid_by_member_id = m.id
            ORDER BY e.expense_date DESC, e.id DESC;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Expense e = mapRow(rs);
                e.setSplits(findSplitsByExpenseId(e.getId(), conn));
                list.add(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve all expenses: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Expense> findRecent(int limit) {
        List<Expense> list = new ArrayList<>();
        String sql = """
            SELECT e.*, g.name AS group_name, m.name AS paid_by_name
            FROM expenses e
            JOIN groups g ON e.group_id = g.id
            JOIN members m ON e.paid_by_member_id = m.id
            ORDER BY e.expense_date DESC, e.id DESC
            LIMIT ?;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find recent expenses: " + e.getMessage(), e);
        }
        return list;
    }

    public BigDecimal getTotalExpenseAmount() {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate total expenses: " + e.getMessage(), e);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getTotalExpenseAmountByGroup(int groupId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE group_id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate group total expenses: " + e.getMessage(), e);
        }
        return BigDecimal.ZERO;
    }

    public List<Expense> searchAndFilter(Integer groupId, ExpenseCategory category,
                                         String searchKeyword, LocalDate fromDate, LocalDate toDate) {
        List<Expense> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT e.*, g.name AS group_name, m.name AS paid_by_name
            FROM expenses e
            JOIN groups g ON e.group_id = g.id
            JOIN members m ON e.paid_by_member_id = m.id
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (groupId != null && groupId > 0) {
            sql.append(" AND e.group_id = ?");
            params.add(groupId);
        }
        if (category != null) {
            sql.append(" AND e.category = ?");
            params.add(category.name());
        }
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            sql.append(" AND (LOWER(e.description) LIKE ? OR LOWER(e.notes) LIKE ?)");
            String kw = "%" + searchKeyword.trim().toLowerCase() + "%";
            params.add(kw);
            params.add(kw);
        }
        if (fromDate != null) {
            sql.append(" AND e.expense_date >= ?");
            params.add(fromDate.toString());
        }
        if (toDate != null) {
            sql.append(" AND e.expense_date <= ?");
            params.add(toDate.toString());
        }

        sql.append(" ORDER BY e.expense_date DESC, e.id DESC;");

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Expense e = mapRow(rs);
                    e.setSplits(findSplitsByExpenseId(e.getId(), conn));
                    list.add(e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search and filter expenses: " + e.getMessage(), e);
        }
        return list;
    }

    public List<ExpenseSplit> findSplitsByExpenseId(int expenseId) {
        try (Connection conn = Database.getConnection()) {
            return findSplitsByExpenseId(expenseId, conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch splits: " + e.getMessage(), e);
        }
    }

    private List<ExpenseSplit> findSplitsByExpenseId(int expenseId, Connection conn) throws SQLException {
        List<ExpenseSplit> splits = new ArrayList<>();
        String sql = """
            SELECT es.*, m.name AS member_name
            FROM expense_splits es
            JOIN members m ON es.member_id = m.id
            WHERE es.expense_id = ?
            ORDER BY es.id ASC;
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, expenseId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int mId = rs.getInt("member_id");
                    String memberName = rs.getString("member_name");
                    BigDecimal shareAmount = rs.getBigDecimal("share_amount");
                    if (shareAmount != null) shareAmount = shareAmount.setScale(com.expensesplitter.util.MoneyUtils.SCALE, com.expensesplitter.util.MoneyUtils.ROUNDING);
                    BigDecimal sharePct = rs.getBigDecimal("share_percentage");
                    if (sharePct != null) sharePct = sharePct.setScale(com.expensesplitter.util.MoneyUtils.SCALE, com.expensesplitter.util.MoneyUtils.ROUNDING);
                    splits.add(new ExpenseSplit(id, expenseId, mId, memberName, shareAmount, sharePct));
                }
            }
        }
        return splits;
    }

    private Expense mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int groupId = rs.getInt("group_id");
        String groupName = rs.getString("group_name");
        String desc = rs.getString("description");
        BigDecimal amount = rs.getBigDecimal("amount");
        if (amount != null) amount = amount.setScale(com.expensesplitter.util.MoneyUtils.SCALE, com.expensesplitter.util.MoneyUtils.ROUNDING);
        int paidById = rs.getInt("paid_by_member_id");
        String paidByName = rs.getString("paid_by_name");
        LocalDate date = LocalDate.parse(rs.getString("expense_date"));
        ExpenseCategory category = ExpenseCategory.fromString(rs.getString("category"));
        String notes = rs.getString("notes");
        SplitMethod method = SplitMethod.fromString(rs.getString("split_method"));
        LocalDateTime createdAt = LocalDateTime.parse(rs.getString("created_at"));
        LocalDateTime updatedAt = LocalDateTime.parse(rs.getString("updated_at"));

        Expense expense = new Expense(id, groupId, desc, amount, paidById, date, category, notes, method, createdAt, updatedAt);
        expense.setGroupName(groupName);
        expense.setPaidByMemberName(paidByName);
        return expense;
    }
}
