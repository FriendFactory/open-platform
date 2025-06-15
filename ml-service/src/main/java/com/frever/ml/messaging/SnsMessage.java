package com.frever.ml.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SnsMessage(@JsonProperty("Subject") String subject,
                         @JsonProperty("MessageId") String messageId,
                         @JsonProperty("Payload") String payload,
                         @JsonProperty("Timestamp") Instant timestamp) {
}
