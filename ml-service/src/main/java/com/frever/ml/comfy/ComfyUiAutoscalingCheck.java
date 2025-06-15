package com.frever.ml.comfy;

import java.time.Instant;

public class ComfyUiAutoscalingCheck {
    private String setupType;
    private Instant checkedAt;
    private Instant aboveThresholdSince;
    private Instant belowThresholdSince;

    public Instant getAboveThresholdSince() {
        return aboveThresholdSince;
    }

    public void setAboveThresholdSince(Instant aboveThresholdSince) {
        this.aboveThresholdSince = aboveThresholdSince;
    }

    public Instant getBelowThresholdSince() {
        return belowThresholdSince;
    }

    public void setBelowThresholdSince(Instant belowThresholdSince) {
        this.belowThresholdSince = belowThresholdSince;
    }

    public String getSetupType() {
        return setupType;
    }

    public void setSetupType(String setupType) {
        this.setupType = setupType;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

}
