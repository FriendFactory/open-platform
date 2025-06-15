package com.frever.ml.comfy.dto;

public enum ContextSwitchLanguageMode {
    English(1),
    EnglishBritish(2),
    French(3),
    Japanese(4),
    Hindi(5),
    MandarinChinese(6),
    Spanish(7),
    BrazilianPortuguese(8),
    Italian(9);
    private final int contextValue;
    public static final ContextSwitchLanguageMode DEFAULT = EnglishBritish;

    public int getContextValue() {
        return contextValue;
    }

    ContextSwitchLanguageMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public static ContextSwitchLanguageMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 9) {
            return DEFAULT;
        }
        for (ContextSwitchLanguageMode value : ContextSwitchLanguageMode.values()) {
            if (value.getContextValue() == contextValue) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid context value: " + contextValue);
    }
}
