package com.frever.ml.comfy.dto;

import org.jboss.resteasy.plugins.providers.multipart.InputPart;

public interface ComfyVideoAndAuxiliaryFileRequest {
    InputPart getVideo();

    InputPart getAuxiliaryFile();

    default boolean allowEmptyAuxiliaryFile() {
        return false;
    }
}
