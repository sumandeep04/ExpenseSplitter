package com.expensesplitter.model;

/**
 * Categories for grouping shared expenses.
 */
public enum GroupCategory {
    TRIP("Trip & Vacation"),
    HOME("Home & Flatmates"),
    FOOD("Food & Hangouts"),
    COLLEGE("College & Projects"),
    OTHER("Other");

    private final String displayName;

    GroupCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static GroupCategory fromString(String text) {
        if (text == null) return OTHER;
        for (GroupCategory cat : GroupCategory.values()) {
            if (cat.name().equalsIgnoreCase(text) || cat.displayName.equalsIgnoreCase(text)) {
                return cat;
            }
        }
        return OTHER;
    }
}
