package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiAutoscalingEvent(@JsonProperty("Subject") String subject,
                                      @JsonProperty("MessageId") String messageId,
                                      @JsonProperty("Message") String message,
                                      @JsonProperty("Timestamp") Instant timestamp) {
}
