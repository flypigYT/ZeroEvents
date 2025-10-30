package com.flypig.zeroEvents.enums;

public enum EventType {
    VASE,
    SHULKER,
    PALM;

    public static EventType fromString(String str) {
        try {
            return EventType.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return VASE;
        }
    }
}