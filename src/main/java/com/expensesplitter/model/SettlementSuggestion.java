package com.expensesplitter.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents an optimized transfer suggestion to settle group debts.
 * E.g., "Bob pays Alice $30.00"
 */
public class SettlementSuggestion {
    private final int fromMemberId;
    private final String fromMemberName;
    private final int toMemberId;
    private final String toMemberName;
    private final BigDecimal amount;

    public SettlementSuggestion(int fromMemberId, String fromMemberName,
                                int toMemberId, String toMemberName,
                                BigDecimal amount) {
        this.fromMemberId = fromMemberId;
        this.fromMemberName = fromMemberName;
        this.toMemberId = toMemberId;
        this.toMemberName = toMemberName;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
    }

    public int getFromMemberId() {
        return fromMemberId;
    }

    public String getFromMemberName() {
        return fromMemberName;
    }

    public int getToMemberId() {
        return toMemberId;
    }

    public String getToMemberName() {
        return toMemberName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return String.format("%s pays %s: %s", fromMemberName, toMemberName, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SettlementSuggestion that = (SettlementSuggestion) o;
        return fromMemberId == that.fromMemberId &&
                toMemberId == that.toMemberId &&
                Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromMemberId, toMemberId, amount);
    }
}
