package com.expensesplitter.model;

/**
 * Standard expense categories supported by the application.
 */
public enum ExpenseCategory {
    FOOD("Food & Dining", "🍔"),
    TRAVEL("Travel & Transport", "🚗"),
    ACCOMMODATION("Accommodation", "🏨"),
    SHOPPING("Shopping", "🛍️"),
    BILLS("Bills & Utilities", "💡"),
    ENTERTAINMENT("Entertainment", "🎬"),
    OTHER("Other", "📦");

    private final String displayName;
    private final String icon;

    ExpenseCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static ExpenseCategory fromString(String text) {
        if (text == null) return OTHER;
        for (ExpenseCategory c : ExpenseCategory.values()) {
            if (c.name().equalsIgnoreCase(text) || c.displayName.equalsIgnoreCase(text)) {
                return c;
            }
        }
        return OTHER;
    }
}
