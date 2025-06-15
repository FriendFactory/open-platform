package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record GroupFollowedMessage(@JsonProperty("FollowingId") long followingId,
                                   @JsonProperty("FollowerId") long followerId,
                                   @JsonProperty("IsMutual") boolean isMutual,
                                   @JsonProperty("Time") Instant time) {
}
