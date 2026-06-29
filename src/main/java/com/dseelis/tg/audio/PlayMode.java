package com.dseelis.tg.audio;

public enum PlayMode {
    SINGLE("Single"),
    LOOP("Loop"),
    SEQUENTIAL("Sequential"),
    SHUFFLE("Shuffle");

    private final String displayName;

    PlayMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PlayMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}
