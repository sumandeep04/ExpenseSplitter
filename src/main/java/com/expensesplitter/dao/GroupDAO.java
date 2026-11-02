package com.expensesplitter.dao;

import com.expensesplitter.database.Database;
import com.expensesplitter.model.Group;
import com.expensesplitter.model.GroupCategory;
import com.expensesplitter.model.Member;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Group entities.
 */
public class GroupDAO {

    private final MemberDAO memberDAO = new MemberDAO();

    public Group create(Group group) {
        String sql = "INSERT INTO groups (name, description, category, created_at, updated_at) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, group.getName().trim());
            pstmt.setString(2, group.getDescription() != null ? group.getDescription().trim() : null);
            pstmt.setString(3, group.getCategory() != null ? group.getCategory().name() : GroupCategory.OTHER.name());
            LocalDateTime now = LocalDateTime.now();
            pstmt.setString(4, now.toString());
            pstmt.setString(5, now.toString());

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        group.setId(keys.getInt(1));
                    }
                }
            }

            // Save initial members if any
            if (group.getMembers() != null && !group.getMembers().isEmpty()) {
                for (Member m : group.getMembers()) {
                    memberDAO.addMemberToGroup(group.getId(), m.getId());
                }
            }

            return group;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create group: " + e.getMessage(), e);
        }
    }

    public void update(Group group) {
        String sql = "UPDATE groups SET name = ?, description = ?, category = ?, updated_at = ? WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, group.getName().trim());
            pstmt.setString(2, group.getDescription() != null ? group.getDescription().trim() : null);
            pstmt.setString(3, group.getCategory() != null ? group.getCategory().name() : GroupCategory.OTHER.name());
            pstmt.setString(4, LocalDateTime.now().toString());
            pstmt.setInt(5, group.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update group: " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM groups WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete group: " + e.getMessage(), e);
        }
    }

    public Group findById(int id) {
        String sql = "SELECT id, name, description, category, created_at, updated_at FROM groups WHERE id = ?;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Group g = mapRow(rs);
                    g.setMembers(memberDAO.findByGroupId(g.getId()));
                    return g;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find group by ID: " + e.getMessage(), e);
        }
        return null;
    }

    public List<Group> findAll() {
        List<Group> list = new ArrayList<>();
        String sql = "SELECT id, name, description, category, created_at, updated_at FROM groups ORDER BY updated_at DESC;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Group g = mapRow(rs);
                g.setMembers(memberDAO.findByGroupId(g.getId()));
                list.add(g);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve groups: " + e.getMessage(), e);
        }
        return list;
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM groups;";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count groups: " + e.getMessage(), e);
        }
        return 0;
    }

    private Group mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        String categoryStr = rs.getString("category");
        GroupCategory category = GroupCategory.fromString(categoryStr);
        String createdAtStr = rs.getString("created_at");
        String updatedAtStr = rs.getString("updated_at");
        LocalDateTime createdAt = createdAtStr != null ? LocalDateTime.parse(createdAtStr) : LocalDateTime.now();
        LocalDateTime updatedAt = updatedAtStr != null ? LocalDateTime.parse(updatedAtStr) : LocalDateTime.now();

        return new Group(id, name, description, category, createdAt, updatedAt);
    }
}
