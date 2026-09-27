package com.poweritem;

import java.util.Locale;

public enum Mode {
    NORMAL, // just normal hits (plus any bonus damage)
    KILL,   // always kills, ignores totems and armor
    POP;    // pops their totem if they have one, otherwise kills

    public static Mode from(String value) {
        if (value == null) return NORMAL;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
