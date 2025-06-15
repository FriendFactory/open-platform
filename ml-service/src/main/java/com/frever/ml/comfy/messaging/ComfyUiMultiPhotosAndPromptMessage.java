package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiMultiPhotosAndPromptMessage(@JsonProperty("Env") String env,
                                                 @JsonProperty("S3Bucket") String s3Bucket,
                                                 @JsonProperty("SourceS3Bucket") String sourceS3Bucket,
                                                 @JsonProperty("InputS3Key") String inputS3Key,
                                                 @JsonProperty("SourceS3Keys") List<String> sourceS3Keys,
                                                 @JsonProperty("GroupId") long groupId,
                                                 @JsonProperty("PromptText") String promptText,
                                                 @JsonProperty("PartialName") String partialName,
                                                 @JsonProperty("ContextValues") List<Integer> contextValues) {
    public String getS3BucketForSource() {
        return sourceS3Bucket != null && !sourceS3Bucket.isEmpty() ? sourceS3Bucket : s3Bucket;
    }
}
