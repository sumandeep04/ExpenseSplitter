package com.expensesplitter.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents an expense logged inside a group.
 */
public class Expense {
    private int id;
    private int groupId;
    private String groupName; // View helper
    private String description;
    private BigDecimal amount;
    private int paidByMemberId;
    private String paidByMemberName; // View helper
    private LocalDate expenseDate;
    private ExpenseCategory category;
    private String notes;
    private SplitMethod splitMethod;
    private List<ExpenseSplit> splits = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Expense() {
        this.amount = BigDecimal.ZERO;
        this.expenseDate = LocalDate.now();
        this.category = ExpenseCategory.OTHER;
        this.splitMethod = SplitMethod.EQUAL;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Expense(int id, int groupId, String description, BigDecimal amount,
                   int paidByMemberId, LocalDate expenseDate, ExpenseCategory category,
                   String notes, SplitMethod splitMethod, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.groupId = groupId;
        this.description = description;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.paidByMemberId = paidByMemberId;
        this.expenseDate = expenseDate != null ? expenseDate : LocalDate.now();
        this.category = category != null ? category : ExpenseCategory.OTHER;
        this.notes = notes;
        this.splitMethod = splitMethod != null ? splitMethod : SplitMethod.EQUAL;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount != null ? amount : BigDecimal.ZERO;
    }

    public int getPaidByMemberId() {
        return paidByMemberId;
    }

    public void setPaidByMemberId(int paidByMemberId) {
        this.paidByMemberId = paidByMemberId;
    }

    public String getPaidByMemberName() {
        return paidByMemberName;
    }

    public void setPaidByMemberName(String paidByMemberName) {
        this.paidByMemberName = paidByMemberName;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public SplitMethod getSplitMethod() {
        return splitMethod;
    }

    public void setSplitMethod(SplitMethod splitMethod) {
        this.splitMethod = splitMethod;
    }

    public List<ExpenseSplit> getSplits() {
        return splits;
    }

    public void setSplits(List<ExpenseSplit> splits) {
        this.splits = splits != null ? splits : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Expense expense = (Expense) o;
        return id == expense.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
