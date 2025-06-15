package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ComfyUiVideoAndAudioAndPromptMessage(@JsonProperty("Env") String env,
                                                   @JsonProperty("S3Bucket") String s3Bucket,
                                                   @JsonProperty("VideoS3Key") String videoS3Key,
                                                   @JsonProperty("GroupId") long groupId,
                                                   @JsonProperty("LevelId") long levelId,
                                                   @JsonProperty("VideoId") long videoId,
                                                   @JsonProperty("Version") String version,
                                                   @JsonProperty("AudioS3Bucket") String audioS3Bucket,
                                                   @JsonProperty("AudioS3Key") String audioS3Key,
                                                   @JsonProperty("PromptText") String promptText,
                                                   @JsonProperty("VideoDurationSeconds") int videoDurationSeconds,
                                                   @JsonProperty("PartialName") String partialName,
                                                   @JsonProperty("AudioStartTime") int audioStartTime,
                                                   @JsonProperty("AudioDuration") int audioDuration,
                                                   @JsonProperty("ContextValues") List<Integer> contextValues,
                                                   @JsonProperty("MediaConvertInfo") MediaConvertInfo mediaConvertInfo)
    implements ComfyUiInputAndAuxiliaryFileInS3 {
    @Override
    public String inputS3Key() {
        return videoS3Key();
    }

    @Override
    public String auxiliaryFileS3Key() {
        return audioS3Key();
    }

    @Override
    public String s3BucketForAuxiliaryFile() {
        return audioS3Bucket() != null && !audioS3Bucket().isBlank() ? audioS3Bucket() : s3Bucket();
    }
}
