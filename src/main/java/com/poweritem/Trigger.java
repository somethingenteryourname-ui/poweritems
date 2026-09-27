package com.poweritem;

import java.util.Locale;

public enum Trigger {
    ATTACK, // only normal melee hits
    USE,    // only damage from using the item (right-click abilities)
    BOTH;   // either one

    public static Trigger from(String value) {
        if (value == null) return ATTACK;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ATTACK;
        }
    }
}
