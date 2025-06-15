package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiInputAndAudioAndPromptMessage(@JsonProperty("Env") String env,
                                                   @JsonProperty("S3Bucket") String s3Bucket,
                                                   @JsonProperty("InputS3Key") String inputS3Key,
                                                   @JsonProperty("AudioS3Bucket") String audioS3Bucket,
                                                   @JsonProperty("AudioS3Key") String audioS3Key,
                                                   @JsonProperty("GroupId") long groupId,
                                                   @JsonProperty("PromptText") String promptText,
                                                   @JsonProperty("PartialName") String partialName,
                                                   @JsonProperty("AudioStartTime") int audioStartTime,
                                                   @JsonProperty("AudioDuration") int audioDuration,
                                                   @JsonProperty("ContextValues") List<Integer> contextValues)
    implements ComfyUiInputAndAuxiliaryFileInS3 {
    @Override
    public String auxiliaryFileS3Key() {
        return audioS3Key();
    }

    @Override
    public String s3BucketForAuxiliaryFile() {
        return audioS3Bucket() != null && !audioS3Bucket().isBlank() ? audioS3Bucket() : s3Bucket();
    }
}
