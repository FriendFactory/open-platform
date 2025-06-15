package com.frever.ml.comfy.dto;

public enum MusicGenBackGroundMusic {
    NarrationInput(1), MixIncomingAudio(2), MuteIncomingAudio(3);

    MusicGenBackGroundMusic(int contextValue) {
        this.contextValue = contextValue;
    }

    private final int contextValue;

    public int getContextValue() {
        return contextValue;
    }

    public static MusicGenBackGroundMusic fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 3) {
            contextValue = 3;
        }
        for (MusicGenBackGroundMusic value : MusicGenBackGroundMusic.values()) {
            if (value.getContextValue() == contextValue) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid context value: " + contextValue);
    }
}
