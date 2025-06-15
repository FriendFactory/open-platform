package com.frever.ml.comfy.dto;

public enum LivePortraitModelMode {
    Human(1),
    Animal(2);

    private final int contextValue;

    LivePortraitModelMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }

    public static final LivePortraitModelMode DEFAULT = Human;

    public static LivePortraitModelMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 2) {
            return DEFAULT;
        }
        for (LivePortraitModelMode mode : LivePortraitModelMode.values()) {
            if (mode.contextValue == contextValue) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
