package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ComfyUiVideoLivePortraitMessage(@JsonProperty("Env") String env,
                                              @JsonProperty("S3Bucket") String s3Bucket,
                                              @JsonProperty("InputS3Key") String inputS3Key,
                                              @JsonProperty("SourceVideoS3Bucket") String sourceVideoS3Bucket,
                                              @JsonProperty("SourceVideoS3Key") String sourceVideoS3Key,
                                              @JsonProperty("SourceAudioS3Bucket") String sourceAudioS3Bucket,
                                              @JsonProperty("SourceAudioS3Key") String sourceAudioS3Key,
                                              @JsonProperty("SourceAudioStartTime") int sourceAudioStartTime,
                                              @JsonProperty("SourceAudioDuration") int sourceAudioDuration,
                                              @JsonProperty("GroupId") long groupId,
                                              @JsonProperty("PartialName") String partialName,
                                              @JsonProperty("LivePortraitAudioInputModeContextValue") int livePortraitAudioInputModeContextValue,
                                              @JsonProperty("LivePortraitCopperModeContextValue") int livePortraitCopperModeContextValue,
                                              @JsonProperty("LivePortraitModelModeContextValue") int livePortraitModelModeContextValue) {
    public String getS3BucketForSourceVideo() {
        return sourceVideoS3Bucket != null && !sourceVideoS3Bucket.isEmpty() ? sourceVideoS3Bucket : s3Bucket;
    }

    public String getS3BucketForSourceAudio() {
        return sourceAudioS3Bucket != null && !sourceAudioS3Bucket.isEmpty() ? sourceAudioS3Bucket : s3Bucket;
    }
}
