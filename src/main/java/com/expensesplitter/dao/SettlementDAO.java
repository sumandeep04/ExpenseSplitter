package com.expensesplitter.dao;

import com.expensesplitter.database.Database;
import com.expensesplitter.model.Settlement;
import com.expensesplitter.model.SettlementStatus;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Settlements and payments between group members.
 */
public class SettlementDAO {

    public Settlement create(Settlement settlement) {
        String sql = """
            INSERT INTO settlements (group_id, from_member_id, to_member_id, amount, 
                                     amount_paid, status, due_date, settlement_date, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, settlement.getGroupId());
            pstmt.setInt(2, settlement.getFromMemberId());
            pstmt.setInt(3, settlement.getToMemberId());
            pstmt.setBigDecimal(4, settlement.getAmount());
            pstmt.setBigDecimal(5, settlement.getAmountPaid() != null ? settlement.getAmountPaid() : BigDecimal.ZERO);
            pstmt.setString(6, settlement.getStatus() != null ? settlement.getStatus().name() : SettlementStatus.PENDING.name());
            pstmt.setString(7, settlement.getDueDate() != null ? settlement.getDueDate().toString() : null);
            pstmt.setString(8, settlement.getSettlementDate() != null ? settlement.getSettlementDate().toString() : null);
            pstmt.setString(9, settlement.getNotes() != null ? settlement.getNotes().trim() : null);
            LocalDateTime now = LocalDateTime.now();
            pstmt.setString(10, now.toString());

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        settlement.setId(keys.getInt(1));
                    }
                }
            }
            return settlement;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create settlement: " + e.getMessage(), e);
        }
    }

    public void update(Settlement settlement) {
        String sql = """
            UPDATE settlements 
            SET amount = ?, amount_paid = ?, status = ?, due_date = ?, settlement_date = ?, notes = ?
            WHERE id = ?;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBigDecimal(1, settlement.getAmount());
            pstmt.setBigDecimal(2, settlement.getAmountPaid());
            pstmt.setString(3, settlement.getStatus().name());
            pstmt.setString(4, settlement.getDueDate() != null ? settlement.getDueDate().toString() : null);
            pstmt.setString(5, settlement.getSettlementDate() != null ? settlement.getSettlementDate().toString() : null);
            pstmt.setString(6, settlement.getNotes() != null ? settlement.getNotes().trim() : null);
            pstmt.setInt(7, settlement.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update settlement: " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM settlements WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete settlement: " + e.getMessage(), e);
        }
    }

    public Settlement findById(int id) {
        String sql = """
            SELECT s.*, g.name AS group_name, m1.name AS from_member_name, m2.name AS to_member_name
            FROM settlements s
            JOIN groups g ON s.group_id = g.id
            JOIN members m1 ON s.from_member_id = m1.id
            JOIN members m2 ON s.to_member_id = m2.id
            WHERE s.id = ?;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find settlement by ID: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Settlement> findByGroupId(int groupId) {
        List<Settlement> list = new ArrayList<>();
        String sql = """
            SELECT s.*, g.name AS group_name, m1.name AS from_member_name, m2.name AS to_member_name
            FROM settlements s
            JOIN groups g ON s.group_id = g.id
            JOIN members m1 ON s.from_member_id = m1.id
            JOIN members m2 ON s.to_member_id = m2.id
            WHERE s.group_id = ?
            ORDER BY s.id DESC;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find settlements for group: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Settlement> findAllPending() {
        List<Settlement> list = new ArrayList<>();
        String sql = """
            SELECT s.*, g.name AS group_name, m1.name AS from_member_name, m2.name AS to_member_name
            FROM settlements s
            JOIN groups g ON s.group_id = g.id
            JOIN members m1 ON s.from_member_id = m1.id
            JOIN members m2 ON s.to_member_id = m2.id
            WHERE s.status = 'PENDING'
            ORDER BY (s.due_date IS NULL), s.due_date ASC, s.id DESC;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending settlements: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Settlement> findAll() {
        List<Settlement> list = new ArrayList<>();
        String sql = """
            SELECT s.*, g.name AS group_name, m1.name AS from_member_name, m2.name AS to_member_name
            FROM settlements s
            JOIN groups g ON s.group_id = g.id
            JOIN members m1 ON s.from_member_id = m1.id
            JOIN members m2 ON s.to_member_id = m2.id
            ORDER BY s.id DESC;
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve settlements: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Records a payment against a settlement.
     * Prevents duplicate completion and accurately tracks partial payments.
     */
    public void recordPayment(int settlementId, BigDecimal paymentAmount, LocalDate settlementDate, String notes) {
        Settlement s = findById(settlementId);
        if (s == null) {
            throw new IllegalArgumentException("Settlement not found with ID: " + settlementId);
        }
        if (s.getStatus() == SettlementStatus.COMPLETED) {
            throw new IllegalStateException("Settlement is already completed. Duplicate payment prevented.");
        }
        if (s.getStatus() == SettlementStatus.CANCELLED) {
            throw new IllegalStateException("Cannot record payment for a cancelled settlement.");
        }

        BigDecimal currentPaid = s.getAmountPaid() != null ? s.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(paymentAmount);

        SettlementStatus newStatus = newPaid.compareTo(s.getAmount()) >= 0
                ? SettlementStatus.COMPLETED
                : SettlementStatus.PENDING;

        String combinedNotes = s.getNotes();
        if (notes != null && !notes.trim().isEmpty()) {
            combinedNotes = (combinedNotes == null || combinedNotes.isEmpty()) ? notes.trim() : combinedNotes + " | " + notes.trim();
        }

        s.setAmountPaid(newPaid);
        s.setStatus(newStatus);
        s.setSettlementDate(settlementDate != null ? settlementDate : LocalDate.now());
        s.setNotes(combinedNotes);

        update(s);
    }

    public void cancelSettlement(int settlementId) {
        Settlement s = findById(settlementId);
        if (s == null) {
            throw new IllegalArgumentException("Settlement not found: " + settlementId);
        }
        s.setStatus(SettlementStatus.CANCELLED);
        update(s);
    }

    private Settlement mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int groupId = rs.getInt("group_id");
        String groupName = rs.getString("group_name");
        int fromMemberId = rs.getInt("from_member_id");
        String fromMemberName = rs.getString("from_member_name");
        int toMemberId = rs.getInt("to_member_id");
        String toMemberName = rs.getString("to_member_name");
        BigDecimal amount = rs.getBigDecimal("amount");
        if (amount != null) amount = amount.setScale(com.expensesplitter.util.MoneyUtils.SCALE, com.expensesplitter.util.MoneyUtils.ROUNDING);
        BigDecimal amountPaid = rs.getBigDecimal("amount_paid");
        if (amountPaid != null) amountPaid = amountPaid.setScale(com.expensesplitter.util.MoneyUtils.SCALE, com.expensesplitter.util.MoneyUtils.ROUNDING);
        SettlementStatus status = SettlementStatus.fromString(rs.getString("status"));
        String dueStr = rs.getString("due_date");
        LocalDate dueDate = (dueStr != null && !dueStr.isEmpty()) ? LocalDate.parse(dueStr) : null;
        String settledStr = rs.getString("settlement_date");
        LocalDate settlementDate = (settledStr != null && !settledStr.isEmpty()) ? LocalDate.parse(settledStr) : null;
        String notes = rs.getString("notes");
        LocalDateTime createdAt = LocalDateTime.parse(rs.getString("created_at"));

        Settlement s = new Settlement(id, groupId, fromMemberId, fromMemberName, toMemberId, toMemberName,
                amount, amountPaid, status, dueDate, settlementDate, notes, createdAt);
        s.setGroupName(groupName);
        return s;
    }
}
