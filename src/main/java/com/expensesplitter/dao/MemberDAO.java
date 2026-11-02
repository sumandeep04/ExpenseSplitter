package com.expensesplitter.dao;

import com.expensesplitter.database.Database;
import com.expensesplitter.model.Member;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Member entities and group memberships.
 */
public class MemberDAO {

    public Member create(Member member) {
        String sql = "INSERT INTO members (name, email, phone, created_at) VALUES (?, ?, ?, ?);";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, member.getName().trim());
            pstmt.setString(2, member.getEmail() != null ? member.getEmail().trim() : null);
            pstmt.setString(3, member.getPhone() != null ? member.getPhone().trim() : null);
            LocalDateTime now = member.getCreatedAt() != null ? member.getCreatedAt() : LocalDateTime.now();
            pstmt.setString(4, now.toString());

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        member.setId(keys.getInt(1));
                    }
                }
            }
            return member;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create member: " + e.getMessage(), e);
        }
    }

    public void update(Member member) {
        String sql = "UPDATE members SET name = ?, email = ?, phone = ? WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, member.getName().trim());
            pstmt.setString(2, member.getEmail() != null ? member.getEmail().trim() : null);
            pstmt.setString(3, member.getPhone() != null ? member.getPhone().trim() : null);
            pstmt.setInt(4, member.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update member: " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        if (isMemberInvolvedInAnyExpenses(id)) {
            throw new IllegalStateException("Cannot delete member because they have associated expense or settlement records.");
        }
        String sql = "DELETE FROM members WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete member: " + e.getMessage(), e);
        }
    }

    public Member findById(int id) {
        String sql = "SELECT id, name, email, phone, created_at FROM members WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find member by ID: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Member> findAll() {
        List<Member> list = new ArrayList<>();
        String sql = "SELECT id, name, email, phone, created_at FROM members ORDER BY name ASC;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve members: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Member> findByGroupId(int groupId) {
        List<Member> list = new ArrayList<>();
        String sql = """
            SELECT m.id, m.name, m.email, m.phone, m.created_at
            FROM members m
            INNER JOIN group_members gm ON m.id = gm.member_id
            WHERE gm.group_id = ?
            ORDER BY m.name ASC;
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
            throw new RuntimeException("Failed to retrieve members for group " + groupId + ": " + e.getMessage(), e);
        }
        return list;
    }

    public void addMemberToGroup(int groupId, int memberId) {
        String sql = "INSERT OR IGNORE INTO group_members (group_id, member_id, joined_at) VALUES (?, ?, ?);";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, memberId);
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add member to group: " + e.getMessage(), e);
        }
    }

    public void removeMemberFromGroup(int groupId, int memberId) {
        if (isMemberInvolvedInGroupExpenses(groupId, memberId)) {
            throw new IllegalStateException("Cannot remove member from group because they have active expense records or settlements in this group.");
        }
        String sql = "DELETE FROM group_members WHERE group_id = ? AND member_id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, memberId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove member from group: " + e.getMessage(), e);
        }
    }

    public boolean isMemberInGroup(int groupId, int memberId) {
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND member_id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, groupId);
            pstmt.setInt(2, memberId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isMemberInvolvedInGroupExpenses(int groupId, int memberId) {
        String paidSql = "SELECT 1 FROM expenses WHERE group_id = ? AND paid_by_member_id = ? LIMIT 1;";
        String splitSql = """
            SELECT 1 FROM expense_splits es
            JOIN expenses e ON es.expense_id = e.id
            WHERE e.group_id = ? AND es.member_id = ? LIMIT 1;
        """;
        String settlementSql = """
            SELECT 1 FROM settlements
            WHERE group_id = ? AND (from_member_id = ? OR to_member_id = ?) LIMIT 1;
        """;
        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement p1 = conn.prepareStatement(paidSql)) {
                p1.setInt(1, groupId);
                p1.setInt(2, memberId);
                try (ResultSet rs = p1.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
            try (PreparedStatement p2 = conn.prepareStatement(splitSql)) {
                p2.setInt(1, groupId);
                p2.setInt(2, memberId);
                try (ResultSet rs = p2.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
            try (PreparedStatement p3 = conn.prepareStatement(settlementSql)) {
                p3.setInt(1, groupId);
                p3.setInt(2, memberId);
                p3.setInt(3, memberId);
                try (ResultSet rs = p3.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed checking member expense involvement: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean isMemberInvolvedInAnyExpenses(int memberId) {
        String paidSql = "SELECT 1 FROM expenses WHERE paid_by_member_id = ? LIMIT 1;";
        String splitSql = "SELECT 1 FROM expense_splits WHERE member_id = ? LIMIT 1;";
        String settlementSql = "SELECT 1 FROM settlements WHERE from_member_id = ? OR to_member_id = ? LIMIT 1;";
        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement p1 = conn.prepareStatement(paidSql)) {
                p1.setInt(1, memberId);
                try (ResultSet rs = p1.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
            try (PreparedStatement p2 = conn.prepareStatement(splitSql)) {
                p2.setInt(1, memberId);
                try (ResultSet rs = p2.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
            try (PreparedStatement p3 = conn.prepareStatement(settlementSql)) {
                p3.setInt(1, memberId);
                p3.setInt(2, memberId);
                try (ResultSet rs = p3.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed checking global member expense involvement: " + e.getMessage(), e);
        }
        return false;
    }

    private Member mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        String createdAtStr = rs.getString("created_at");
        LocalDateTime createdAt = createdAtStr != null ? LocalDateTime.parse(createdAtStr) : LocalDateTime.now();
        return new Member(id, name, email, phone, createdAt);
    }
}
