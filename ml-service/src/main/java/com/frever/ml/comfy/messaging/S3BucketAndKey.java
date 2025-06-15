package com.frever.ml.comfy.messaging;

public interface S3BucketAndKey {
    String s3Bucket();

    String s3Key();

    static S3BucketAndKey create(String s3Bucket, String s3Key) {
        return new S3BucketAndKeyImpl(s3Bucket, s3Key);
    }

    record S3BucketAndKeyImpl(String s3Bucket, String s3Key) implements S3BucketAndKey {
    }
}
