package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiMultiResultResponse(String bucket, String mainKey, String coverKey, String thumbnailKey,
                                         String maskKey) {
    public ComfyUiMultiResultResponse(String bucket, String mainKey) {
        this(bucket, mainKey, null, null, null);
    }
}
