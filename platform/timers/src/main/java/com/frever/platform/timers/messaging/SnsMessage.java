package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record SnsMessage(@JsonProperty("Subject") String subject,
                         @JsonProperty("MessageId") String messageId,
                         @JsonProperty("Payload") String payload,
                         @JsonProperty("Timestamp") Instant timestamp) {
}
