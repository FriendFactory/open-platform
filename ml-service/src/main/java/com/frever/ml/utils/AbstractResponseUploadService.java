package com.frever.ml.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public abstract class AbstractResponseUploadService {
    @Inject
    protected S3UploadService s3UploadService;
    @Inject
    protected ObjectMapper objectMapper;

    private static final String BUCKET_NAME = "frever-machine-learning-sagemaker";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    protected void uploadResponseToS3(long groupId, String keyPrefix, Object response, String logMessage) {
        if (shouldUpload()) {
            var epochSecond = Instant.now().getEpochSecond();
            var wrappedResponse = new WrappedResponse(groupId, epochSecond, response);
            String key = getKey(groupId, epochSecond, keyPrefix);
            try {
                String jsonResponse = objectMapper.writeValueAsString(wrappedResponse);
                s3UploadService.uploadResponseToS3(
                    BUCKET_NAME,
                    key,
                    jsonResponse
                );
            } catch (Exception e) {
                Log.warnf(e, "Failed to upload response to key %s in bucket %s.", key, BUCKET_NAME);
            }
            Log.info(logMessage + ", key: " + key);
        }
    }

    protected String getKey(long groupId, long epochSecond, String keyPrefix) {
        var folderName = LocalDate.now().format(FORMATTER);
        return keyPrefix + folderName + "/" + groupId + "-" + epochSecond + ".json";
    }

    protected boolean shouldUpload() {
        return ConfigUtils.getProfiles().contains("prod");
    }

    private record WrappedResponse(long requestGroupId, long timestamp, Object response) {
    }
}
