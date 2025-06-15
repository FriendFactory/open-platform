package com.frever.ml.comfy;

import static com.frever.ml.comfy.ComfyUiManager.MASK_FORMAT;
import static com.frever.ml.comfy.ComfyUiManager.THUMBNAIL_COVER_FORMAT;
import static com.frever.ml.comfy.ComfyUiManager.THUMBNAIL_MAIN_FORMAT;
import static com.frever.ml.comfy.ComfyUiManager.THUMBNAIL_THUMBNAIL_FORMAT;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Singleton
@Unremovable
public class ComfyUiResponsePublisher {
    public static final String FAILED_TO_FIND_RESULT = "Failed to find the result.";

    SnsClient sns;
    String responseTopicArn;

    @Inject
    ObjectMapper objectMapper;
    @Inject
    ComfyProperties comfyProperties;

    @PostConstruct
    protected void init() {
        sns = SnsClient.builder().region(Region.EU_CENTRAL_1).build();
        responseTopicArn = comfyProperties.responseTopicArn();
    }

    public void resultGeneratedSuccessfully(String partialName, List<String> s3Keys) {
        if (s3Keys.size() == 1) {
            resultGeneratedSuccessfully(partialName, s3Keys.getFirst(), "", " ", "");
        } else {
            String mainKey = "", coverKey = "", thumbnailKey = "", maskKey = "";
            for (var s3Key : s3Keys) {
                if (s3Key.contains(THUMBNAIL_MAIN_FORMAT)) {
                    mainKey = s3Key;
                } else if (s3Key.contains(THUMBNAIL_COVER_FORMAT)) {
                    coverKey = s3Key;
                } else if (s3Key.contains(MASK_FORMAT)) {
                    maskKey = s3Key;
                } else if (s3Key.contains(THUMBNAIL_THUMBNAIL_FORMAT)) {
                    thumbnailKey = s3Key;
                }
            }
            resultGeneratedSuccessfully(partialName, mainKey, coverKey, thumbnailKey, maskKey);
        }
    }

    private void resultGeneratedSuccessfully(
        String partialName,
        String mainKey,
        String coverKey,
        String thumbnailKey,
        String maskKey
    ) {
        try {
            var bucket = comfyProperties.resultS3Bucket();
            var payload = new ComfyUiResult(bucket, partialName, mainKey, coverKey, thumbnailKey, maskKey);
            var now = Instant.now();
            var messageEnvelope = new MessageEnvelope(
                UUID.randomUUID().toString(),
                ComfyUiResult.SUBJECT,
                MessageEnvelope.DEFAULT_VERSION,
                ComfyUiResult.TYPE,
                now,
                objectMapper.writeValueAsString(payload)
            );
            String message = objectMapper.writeValueAsString(messageEnvelope);
            sns.publish(builder -> {
                builder.topicArn(responseTopicArn);
                builder.message(message);
            });
        } catch (Exception e) {
            Log.warnf(e, "Error while publishing SNS message.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ComfyUiResult(@JsonProperty("Bucket") String bucket, @JsonProperty("PartialName") String partialName,
                         @JsonProperty("MainKey") String mainKey, @JsonProperty("CoverKey") String coverKey,
                         @JsonProperty("ThumbnailKey") String thumbnailKey, @JsonProperty("MaskKey") String maskKey) {
        static final String SUBJECT = "ComfyUiResult";
        static final String TYPE = "Frever.Client.Shared.AI.ComfyUi.Contract.ComfyUiResult";
    }

    public void failToGenerateResult(String partialName, String error) {
        try {
            var payload = new ComfyUiError(partialName, error);
            var now = Instant.now();
            var messageEnvelope = new MessageEnvelope(
                UUID.randomUUID().toString(),
                ComfyUiError.SUBJECT,
                MessageEnvelope.DEFAULT_VERSION,
                ComfyUiError.TYPE,
                now,
                objectMapper.writeValueAsString(payload)
            );
            String message = objectMapper.writeValueAsString(messageEnvelope);
            sns.publish(builder -> {
                builder.topicArn(responseTopicArn);
                builder.message(message);
            });
        } catch (Exception e) {
            Log.warnf(e, "Error while publishing SNS message.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ComfyUiError(@JsonProperty("PartialName") String partialName, @JsonProperty("Error") String error) {
        static final String SUBJECT = "ComfyUiError";
        static final String TYPE = "Frever.Client.Shared.AI.ComfyUi.Contract.ComfyUiError";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MessageEnvelope(String id, String source, @JsonProperty("specversion") String version, String type,
                           Instant time, String data) {
        static final String DEFAULT_VERSION = "1.0";
    }
}
