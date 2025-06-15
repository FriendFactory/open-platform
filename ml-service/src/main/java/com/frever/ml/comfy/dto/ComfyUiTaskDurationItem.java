package com.frever.ml.comfy.dto;

import java.time.Instant;
import java.util.UUID;

public record ComfyUiTaskDurationItem(long id, UUID promptId, String serverIp, int duration, String workflow,
                                      Instant startedAt) {
}
