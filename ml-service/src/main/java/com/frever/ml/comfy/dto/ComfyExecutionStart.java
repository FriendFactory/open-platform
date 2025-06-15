package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyExecutionStart(@JsonProperty("prompt_id") String promptId,
                                  @JsonProperty("timestamp") long timestamp) {
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(timestamp);
    }
}
