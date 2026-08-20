package com.cookiebuild.skywars.ui;

enum KitMenuAction {
    SELECT,
    PURCHASE,
    CONFIRM_PURCHASE,
    BACK;

    static KitMenuAction from(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
