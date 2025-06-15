package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiResultRequest(@JsonProperty("Env") String env,
                                   @JsonProperty("S3Bucket") String s3Bucket,
                                   @JsonProperty("InputS3Key") String inputS3Key,
                                   @JsonProperty("GroupId") long groupId,
                                   @JsonProperty("Workflow") String workflow,
                                   @JsonProperty("PartialName") String partialName) {
}
