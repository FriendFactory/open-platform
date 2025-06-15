package com.frever.ml.comfy.dto;

public enum LivePortraitAudioInputMode {
    InputAudio(1),
    InputVideoDrivingAudio(2),
    AudioEmbeddedInTargetVideo(3);

    private int contextValue;

    LivePortraitAudioInputMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }

    public static final LivePortraitAudioInputMode DEFAULT = InputAudio;

    public static LivePortraitAudioInputMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 3) {
            return DEFAULT;
        }
        for (LivePortraitAudioInputMode mode : LivePortraitAudioInputMode.values()) {
            if (mode.contextValue == contextValue) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
