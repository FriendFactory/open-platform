package com.frever.ml.comfy.messaging;

import com.frever.ml.comfy.ComfyUiResultType;
import java.time.Instant;

public record OngoingPromptRequest(String comfyIp, String fileName, String partialName, String postJson,
                                   Instant submittedAt, long comfyUiTaskId, MediaConvertInfo mediaConvertInfo,
                                   ComfyUiResultType resultType) {
}
