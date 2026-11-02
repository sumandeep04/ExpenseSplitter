package com.expensesplitter.model;

/**
 * Represents the splitting method used for distributing an expense among participants.
 */
public enum SplitMethod {
    EQUAL("Equal Split"),
    CUSTOM("Unequal / Custom Split"),
    PERCENTAGE("Percentage Split");

    private final String displayName;

    SplitMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static SplitMethod fromString(String text) {
        if (text == null) return EQUAL;
        for (SplitMethod method : SplitMethod.values()) {
            if (method.name().equalsIgnoreCase(text) || method.displayName.equalsIgnoreCase(text)) {
                return method;
            }
        }
        return EQUAL;
    }
}
