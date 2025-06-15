package com.frever.ml.comfy.setup;

import com.frever.ml.comfy.ComfyUiAutoscalingCheck;

@FunctionalInterface
public interface ComfyUiAutoscalingCheckCallback {
    ComfyUiAutoscalingCheck comfyUiAutoscalingCheck(ComfyUiAutoscalingCheck existingCheck);
}
