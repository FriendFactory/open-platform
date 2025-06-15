package com.frever.ml.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@ApplicationScoped
public class S3UploadService extends AsyncServiceUsingOwnThreadPool {
    S3Client s3;

    @PostConstruct
    protected void init() {
        super.init();
        s3 = S3Client.builder()
            .region(Region.EU_CENTRAL_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @PreDestroy
    protected void destroy() {
        super.destroy();
        s3.close();
    }

    public void uploadResponseToS3(String bucketName, String key, String jsonResponse) {
        executor.submit(() -> {
            s3.putObject(builder -> builder.bucket(bucketName).key(key), RequestBody.fromString(jsonResponse));
        });
    }
}
