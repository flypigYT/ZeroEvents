package com.flypig.zeroEvents.enums;

public enum EventRarity {
    DEFAULT,
    RARE,
    MYTHIC,
    LEGENDARY;

    public static EventRarity fromString(String str) {
        try {
            return EventRarity.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}