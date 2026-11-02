package com.expensesplitter.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a debt settlement / payment between two members of a group.
 */
public class Settlement {
    private int id;
    private int groupId;
    private String groupName;
    private int fromMemberId;
    private String fromMemberName;
    private int toMemberId;
    private String toMemberName;
    private BigDecimal amount;
    private BigDecimal amountPaid;
    private SettlementStatus status;
    private LocalDate dueDate;
    private LocalDate settlementDate;
    private String notes;
    private LocalDateTime createdAt;

    public Settlement() {
        this.amount = BigDecimal.ZERO;
        this.amountPaid = BigDecimal.ZERO;
        this.status = SettlementStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Settlement(int id, int groupId, int fromMemberId, String fromMemberName,
                      int toMemberId, String toMemberName, BigDecimal amount, BigDecimal amountPaid,
                      SettlementStatus status, LocalDate dueDate, LocalDate settlementDate,
                      String notes, LocalDateTime createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.fromMemberId = fromMemberId;
        this.fromMemberName = fromMemberName;
        this.toMemberId = toMemberId;
        this.toMemberName = toMemberName;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.amountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        this.status = status != null ? status : SettlementStatus.PENDING;
        this.dueDate = dueDate;
        this.settlementDate = settlementDate;
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
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

    public int getFromMemberId() {
        return fromMemberId;
    }

    public void setFromMemberId(int fromMemberId) {
        this.fromMemberId = fromMemberId;
    }

    public String getFromMemberName() {
        return fromMemberName;
    }

    public void setFromMemberName(String fromMemberName) {
        this.fromMemberName = fromMemberName;
    }

    public int getToMemberId() {
        return toMemberId;
    }

    public void setToMemberId(int toMemberId) {
        this.toMemberId = toMemberId;
    }

    public String getToMemberName() {
        return toMemberName;
    }

    public void setToMemberName(String toMemberName) {
        this.toMemberName = toMemberName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount != null ? amount : BigDecimal.ZERO;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public void setSettlementDate(LocalDate settlementDate) {
        this.settlementDate = settlementDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getRemainingAmount() {
        if (amount == null) return BigDecimal.ZERO;
        BigDecimal paid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        BigDecimal remaining = amount.subtract(paid);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    public boolean isFullyPaid() {
        return status == SettlementStatus.COMPLETED ||
               (amount != null && amountPaid != null && amountPaid.compareTo(amount) >= 0);
    }

    public boolean isOverdue() {
        if (status == SettlementStatus.COMPLETED || status == SettlementStatus.CANCELLED) {
            return false;
        }
        return dueDate != null && dueDate.isBefore(LocalDate.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settlement that = (Settlement) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
