package com.frever.ml.comfy.dto;

public enum LivePortraitCopperMode {
    IfCopperModeCuda(1),
    MpCopperModeCuda(2);

    private final int contextValue;

    LivePortraitCopperMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }

    public static final LivePortraitCopperMode DEFAULT = IfCopperModeCuda;

    public static LivePortraitCopperMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 2) {
            return DEFAULT;
        }
        for (LivePortraitCopperMode mode : LivePortraitCopperMode.values()) {
            if (mode.contextValue == contextValue) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
