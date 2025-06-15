package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyImage(String name,
                         String subfolder,
                         String type,
                         String comfyImageUrl,
                         String viewUrl
) {
}
