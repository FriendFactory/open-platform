package com.frever.ml.comfy.dto;

public enum MmAudioPromptMode {
    AutoPrompt(1), PromptOnly(2), AutoPromptPlusPromptAppend(3);
    private final int contextValue;
    public static final MmAudioPromptMode DEFAULT = AutoPrompt;

    MmAudioPromptMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }

    public static MmAudioPromptMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 3) {
            contextValue = 1;
        }
        for (MmAudioPromptMode value : MmAudioPromptMode.values()) {
            if (value.getContextValue() == contextValue) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid context value: " + contextValue);
    }
}
