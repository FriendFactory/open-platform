package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiLatentSyncMessage(@JsonProperty("Env") String env, @JsonProperty("S3Bucket") String s3Bucket,
                                       @JsonProperty("AudioS3Bucket") String audioS3Bucket,
                                       @JsonProperty("VideoS3Key") String inputS3Key,
                                       @JsonProperty("AudioS3Key") String audioS3Key,
                                       @JsonProperty("GroupId") long groupId, @JsonProperty("LevelId") long levelId,
                                       @JsonProperty("VideoId") long videoId, @JsonProperty("Version") String version,
                                       @JsonProperty("VideoDurationSeconds") int videoDurationSeconds,
                                       @JsonProperty("StartTimeSeconds") int startTimeSeconds,
                                       @JsonProperty("ResultVideoDurationSeconds") int resultVideoDurationSeconds,
                                       @JsonProperty("PartialName") String partialName,
                                       @JsonProperty("MediaConvertInfo") MediaConvertInfo mediaConvertInfo)
    implements ComfyUiInputAndAuxiliaryFileInS3 {
    @Override
    public String auxiliaryFileS3Key() {
        return audioS3Key;
    }

    @Override
    public String s3BucketForAuxiliaryFile() {
        return audioS3Bucket != null && !audioS3Bucket.isEmpty() ? audioS3Bucket : s3Bucket;
    }
}
