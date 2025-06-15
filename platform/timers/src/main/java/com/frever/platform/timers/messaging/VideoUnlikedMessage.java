package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record VideoUnlikedMessage(@JsonProperty("GroupId") long groupId,
                                  @JsonProperty("VideoId") long videoId,
                                  @JsonProperty("Time") Instant time) {
}
