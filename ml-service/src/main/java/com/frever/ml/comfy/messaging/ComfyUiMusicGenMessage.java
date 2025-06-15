package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ComfyUiMusicGenMessage(@JsonProperty("Env") String env,
                                     @JsonProperty("S3Bucket") String s3Bucket,
                                     @JsonProperty("InputS3Key") String inputS3Key,
                                     @JsonProperty("GroupId") long groupId,
                                     @JsonProperty("PromptText") String promptText,
                                     @JsonProperty("BackGroundMusicContextValue") int backGroundMusicContextValue,
                                     @JsonProperty("PartialName") String partialName) implements S3BucketAndKey {
    @Override
    public String s3Key() {
        return inputS3Key();
    }
}
