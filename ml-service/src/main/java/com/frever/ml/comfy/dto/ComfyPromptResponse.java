package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyPromptResponse(@JsonProperty("prompt_id") String promptId,
                                  String number,
                                  @JsonProperty("node_errors") Object nodeErrors
) {
}
