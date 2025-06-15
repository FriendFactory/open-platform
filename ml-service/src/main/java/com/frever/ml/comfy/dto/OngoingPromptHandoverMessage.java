package com.frever.ml.comfy.dto;

import com.frever.ml.comfy.ComfyUiResultType;
import com.frever.ml.comfy.messaging.MediaConvertInfo;
import java.time.Instant;

public record OngoingPromptHandoverMessage(String serverIpAndPort, String promptId, String filename, String partialName,
                                           Instant submittedAt, MediaConvertInfo mediaConvertInfo,
                                           ComfyUiResultType resultType) {
}
