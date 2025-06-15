package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiPhotoResultRequest(@JsonProperty("Env") String env,
                                        @JsonProperty("S3Bucket") String s3Bucket,
                                        @JsonProperty("InputS3Key") String inputS3Key,
                                        @JsonProperty("GroupId") long groupId,
                                        @JsonProperty("PhotoWorkflow") String photoWorkflow,
                                        @JsonProperty("PartialName") String partialName) {
    public static ComfyUiResultRequest toComfyUiResultRequest(ComfyUiPhotoResultRequest request) {
        return new ComfyUiResultRequest(
            request.env(),
            request.s3Bucket(),
            request.inputS3Key(),
            request.groupId(),
            request.photoWorkflow(),
            request.partialName()
        );
    }
}
