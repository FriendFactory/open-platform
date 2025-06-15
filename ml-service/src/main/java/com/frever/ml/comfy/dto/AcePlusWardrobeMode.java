package com.frever.ml.comfy.dto;

public enum AcePlusWardrobeMode {
    FullClothes(1),
    Hair(2),
    Hats(3),
    Glasses(4),
    Bags(5),
    Necklace(6),
    Shoes(7),
    FullClothesAndHairNoFace(8);

    private final int contextValue;
    public static final AcePlusWardrobeMode DEFAULT = FullClothes;

    AcePlusWardrobeMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }

    public static AcePlusWardrobeMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 8) {
            return DEFAULT;
        }
        for (AcePlusWardrobeMode mode : AcePlusWardrobeMode.values()) {
            if (mode.contextValue == contextValue) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
