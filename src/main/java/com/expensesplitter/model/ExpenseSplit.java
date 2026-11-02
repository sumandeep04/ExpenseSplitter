package com.expensesplitter.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents an individual member's share in an expense.
 */
public class ExpenseSplit {
    private int id;
    private int expenseId;
    private int memberId;
    private String memberName; // Presentation helper
    private BigDecimal shareAmount;
    private BigDecimal sharePercentage;

    public ExpenseSplit() {
        this.shareAmount = BigDecimal.ZERO;
    }

    public ExpenseSplit(int id, int expenseId, int memberId, String memberName,
                        BigDecimal shareAmount, BigDecimal sharePercentage) {
        this.id = id;
        this.expenseId = expenseId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.shareAmount = shareAmount != null ? shareAmount : BigDecimal.ZERO;
        this.sharePercentage = sharePercentage;
    }

    public ExpenseSplit(int memberId, String memberName, BigDecimal shareAmount) {
        this(0, 0, memberId, memberName, shareAmount, null);
    }

    public ExpenseSplit(int memberId, String memberName, BigDecimal shareAmount, BigDecimal sharePercentage) {
        this(0, 0, memberId, memberName, shareAmount, sharePercentage);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(int expenseId) {
        this.expenseId = expenseId;
    }

    public int getMemberId() {
        return memberId;
    }

    public void setMemberId(int memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    public void setShareAmount(BigDecimal shareAmount) {
        this.shareAmount = shareAmount != null ? shareAmount : BigDecimal.ZERO;
    }

    public BigDecimal getSharePercentage() {
        return sharePercentage;
    }

    public void setSharePercentage(BigDecimal sharePercentage) {
        this.sharePercentage = sharePercentage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpenseSplit that = (ExpenseSplit) o;
        return memberId == that.memberId && Objects.equals(expenseId, that.expenseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expenseId, memberId);
    }
}
