package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupUnfollowedMessage(@JsonProperty("FollowingId") long followingId,
                                     @JsonProperty("FollowerId") long followerId,
                                     @JsonProperty("IsMutual") boolean isMutual,
                                     @JsonProperty("Time") Instant time,
                                     @JsonProperty("UnfollowedTime") Instant unfollowedTime) {
}
