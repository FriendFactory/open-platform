package com.frever.ml.comfy.dto;

public enum MmAudioAudioMode {
    MuteIncomingAudioBackgroundMusicNoVoices(1),
    MuteIncomingAudioBackgroundMusicWithVoices(2),
    NarrationInputBackgroundMusicNoVoices(3),
    MixIncomingAudioBackgroundAudioWithSfxNoVoices(4),
    NarrationInputBackgroundMusicWithVoices(5),
    MixIncomingAudioBackgroundAudioWithSfxWithVoices(6),
    MixIncomingAudioBackgroundAudioWithSfxNoVoicesLowerVolume(7),
    MixIncomingAudioBackgroundAudioWithSfxWithVoicesLowerVolume(8);

    private final int contextValue;
    public static final MmAudioAudioMode DEFAULT = MuteIncomingAudioBackgroundMusicNoVoices;

    MmAudioAudioMode(int contextValue) {
        this.contextValue = contextValue;
    }

    public int getContextValue() {
        return contextValue;
    }

    public static MmAudioAudioMode fromContextValue(int contextValue) {
        if (contextValue < 1 || contextValue > 8) {
            return DEFAULT;
        }
        for (MmAudioAudioMode value : MmAudioAudioMode.values()) {
            if (value.getContextValue() == contextValue) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid context value: " + contextValue);
    }

}
