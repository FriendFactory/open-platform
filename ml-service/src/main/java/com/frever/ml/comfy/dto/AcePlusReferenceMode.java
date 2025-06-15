package com.frever.ml.comfy.dto;

public enum AcePlusReferenceMode {
    UploadImage(1),
    Prompt(2);

    private final int contextValue;
    public static final AcePlusReferenceMode DEFAULT = UploadImage;

    AcePlusReferenceMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }
    
    public static AcePlusReferenceMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 2) {
            return DEFAULT;
        }
        for (AcePlusReferenceMode mode : AcePlusReferenceMode.values()) {
            if (mode.contextValue == contextValue) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
