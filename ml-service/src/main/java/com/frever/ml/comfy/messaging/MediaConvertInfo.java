package com.frever.ml.comfy.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaConvertInfo(@JsonProperty("VideoId") Long videoId,
                               @JsonProperty("MediaConvertJobSqsQueue") String mediaConvertJobSqsQueue,
                               @JsonProperty("DestinationBucketPath") String destinationBucketPath,
                               @JsonProperty("MediaConvertJobRoleArn") String mediaConvertJobRoleArn,
                               @JsonProperty("MediaConvertVideoJobTemplateName") String mediaConvertVideoJobTemplateName,
                               @JsonProperty("MediaConvertVideoThumbnailJobTemplateName") String mediaConvertVideoThumbnailJobTemplateName,
                               @JsonProperty("MediaConvertQueue") String mediaConvertQueue,
                               @JsonProperty("HasLandscapeOrientation") boolean hasLandscapeOrientation) {
    public static MediaConvertInfo forTestInDevEnv(long videoId, String destinationBucketPath) {
        return new MediaConvertInfo(
            videoId,
            "https://sqs.eu-central-1.amazonaws.com/722913253728/dev-1-video-conversion-job-creation",
            destinationBucketPath,
            "arn:aws:iam::722913253728:role/sergii-dev-media-convert-manual-role",
            "video-conversion-stage",
            "video-thumbnail",
            "dev-1",
            false
        );
    }
}
