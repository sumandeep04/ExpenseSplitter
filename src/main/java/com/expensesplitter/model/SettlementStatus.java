package com.expensesplitter.model;

/**
 * Status of a settlement transaction or payment request.
 */
public enum SettlementStatus {
    PENDING("Pending"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    SettlementStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static SettlementStatus fromString(String text) {
        if (text == null) return PENDING;
        for (SettlementStatus status : SettlementStatus.values()) {
            if (status.name().equalsIgnoreCase(text) || status.displayName.equalsIgnoreCase(text)) {
                return status;
            }
        }
        return PENDING;
    }
}
