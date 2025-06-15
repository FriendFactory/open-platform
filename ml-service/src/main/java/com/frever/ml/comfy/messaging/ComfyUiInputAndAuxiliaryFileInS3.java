package com.frever.ml.comfy.messaging;

public interface ComfyUiInputAndAuxiliaryFileInS3 {
    String s3Bucket();

    String inputS3Key();

    String auxiliaryFileS3Key();

    long groupId();

    default String s3BucketForAuxiliaryFile() {
        return s3Bucket();
    }

    default String partialName() {
        return "";
    }
}
