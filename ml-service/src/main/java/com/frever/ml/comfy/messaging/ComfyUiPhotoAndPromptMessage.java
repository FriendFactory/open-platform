package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiPhotoAndPromptMessage(@JsonProperty("Env") String env,
                                           @JsonProperty("S3Bucket") String s3Bucket,
                                           @JsonProperty("InputS3Key") String inputS3Key,
                                           @JsonProperty("GroupId") long groupId,
                                           @JsonProperty("PartialName") String partialName,
                                           @JsonProperty("PromptText") String promptText) implements S3BucketAndKey {
    @Override
    public String s3Key() {
        return inputS3Key;
    }
}
