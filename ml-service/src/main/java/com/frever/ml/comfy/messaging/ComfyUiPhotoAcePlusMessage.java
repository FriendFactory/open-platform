package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ComfyUiPhotoAcePlusMessage(@JsonProperty("Env") String env,
                                         @JsonProperty("S3Bucket") String s3Bucket,
                                         @JsonProperty("SourceS3Bucket") String sourceS3Bucket,
                                         @JsonProperty("MaskS3Bucket") String maskS3Bucket,
                                         @JsonProperty("InputS3Key") String inputS3Key,
                                         @JsonProperty("SourceS3Key") String sourceS3Key,
                                         @JsonProperty("MaskS3Key") String maskS3Key,
                                         @JsonProperty("GroupId") long groupId,
                                         @JsonProperty("PromptText") String promptText,
                                         @JsonProperty("PartialName") String partialName,
                                         @JsonProperty("AcePlusWardrobeModeContextValue") int AcePlusWardrobeModeContextValue,
                                         @JsonProperty("AcePlusReferenceModeContextValue") int AcePlusReferenceModeContextValue,
                                         @JsonProperty("AcePlusMaskModeContextValue") int AcePlusMaskModeContextValue) {
    public String getS3BucketForSource() {
        return sourceS3Bucket != null && !sourceS3Bucket.isEmpty() ? sourceS3Bucket : s3Bucket;
    }

    public String getS3BucketForMask() {
        return maskS3Bucket != null && !maskS3Bucket.isEmpty() ? maskS3Bucket : s3Bucket;
    }
}
