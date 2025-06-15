package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiPhotoMakeUpMessage(@JsonProperty("Env") String env,
                                        @JsonProperty("S3Bucket") String s3Bucket,
                                        @JsonProperty("MakeUpS3Bucket") String makeUpS3Bucket,
                                        @JsonProperty("InputS3Key") String inputS3Key,
                                        @JsonProperty("MakeUpS3Key") String makeUpS3Key,
                                        @JsonProperty("GroupId") long groupId) {
    public String getS3BucketForMakeUp() {
        return makeUpS3Bucket != null && !makeUpS3Bucket.isEmpty() ? makeUpS3Bucket : s3Bucket;
    }
}
