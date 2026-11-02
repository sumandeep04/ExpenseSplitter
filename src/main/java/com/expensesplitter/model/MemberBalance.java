package com.expensesplitter.model;

import java.math.BigDecimal;

/**
 * Encapsulates the net financial position of a member within a group.
 * Formula: Net Balance = Total Paid - Total Expense Share
 */
public class MemberBalance {
    private final int memberId;
    private final String memberName;
    private final BigDecimal totalPaid;
    private final BigDecimal totalShare;
    private final BigDecimal netBalance;

    public MemberBalance(int memberId, String memberName, BigDecimal totalPaid, BigDecimal totalShare) {
        this.memberId = memberId;
        this.memberName = memberName;
        this.totalPaid = totalPaid != null ? totalPaid : BigDecimal.ZERO;
        this.totalShare = totalShare != null ? totalShare : BigDecimal.ZERO;
        this.netBalance = this.totalPaid.subtract(this.totalShare);
    }

    public int getMemberId() {
        return memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public BigDecimal getTotalShare() {
        return totalShare;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public boolean isCreditor() {
        return netBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isDebtor() {
        return netBalance.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isSettled() {
        return netBalance.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public String toString() {
        return String.format("%s [Paid: %s, Share: %s, Net: %s]", memberName, totalPaid, totalShare, netBalance);
    }
}
