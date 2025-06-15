package com.frever.ml.comfy.dto;

public enum AcePlusMaskMode {
    AutoMask(1),
    ManualMask(2);

    private final int contextValue;
    public static final AcePlusMaskMode DEFAULT = AutoMask;

    AcePlusMaskMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }
    
    public static AcePlusMaskMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 2) {
            return DEFAULT;
        }
        for (AcePlusMaskMode mode : AcePlusMaskMode.values()) {
            if (mode.contextValue == contextValue) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
