package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyExecutionError(@JsonProperty("prompt_id") String promptId,
                                  @JsonProperty("node_id") int nodeId,
                                  @JsonProperty("node_type") String nodeType,
                                  @JsonProperty("exception_message") String exceptionMessage,
                                  @JsonProperty("exception_type") String exceptionType) {
}
