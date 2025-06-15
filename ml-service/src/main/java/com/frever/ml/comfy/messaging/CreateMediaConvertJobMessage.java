package com.frever.ml.comfy.messaging;

import static com.frever.ml.utils.Utils.normalizeS3Path;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateMediaConvertJobMessage(@JsonProperty("VideoId") long videoId,
                                           @JsonProperty("RoleArn") String roleArn,
                                           @JsonProperty("JobTemplateName") String jobTemplateName,
                                           @JsonProperty("Queue") String queue,
                                           @JsonProperty("UserMetadata") Map<String, String> userMetadata,
                                           @JsonProperty("SourceBucketPath") String sourceBucketPath,
                                           @JsonProperty("DestinationBucketPath") String destinationBucketPath,
                                           @JsonProperty("HasLandscapeOrientation") boolean hasLandscapeOrientation) {

    public static CreateMediaConvertJobMessage[] fromMediaConvertInfo(
        MediaConvertInfo mediaConvertInfo,
        String sourceBucketPath
    ) {
        var video = getCreateMediaConvertJobMessageForVideo(mediaConvertInfo, sourceBucketPath);
        var thumbnail = getCreateMediaConvertJobMessageForThumbnail(mediaConvertInfo, sourceBucketPath);
        return new CreateMediaConvertJobMessage[]{video, thumbnail};
    }

    private static CreateMediaConvertJobMessage getCreateMediaConvertJobMessageForThumbnail(
        MediaConvertInfo mediaConvertInfo,
        String sourceBucketPath
    ) {
        var job2UserMetadata = new HashMap<String, String>();
        job2UserMetadata.put("VideoId", mediaConvertInfo.videoId().toString());
        job2UserMetadata.put("ConversionType", "Thumbnail");
        job2UserMetadata.put("V", "2");
        return new CreateMediaConvertJobMessage(
            mediaConvertInfo.videoId(),
            mediaConvertInfo.mediaConvertJobRoleArn(),
            mediaConvertInfo.mediaConvertVideoThumbnailJobTemplateName(),
            mediaConvertInfo.mediaConvertQueue(),
            job2UserMetadata,
            sourceBucketPath,
            normalizeS3Path(mediaConvertInfo.destinationBucketPath()),
            mediaConvertInfo.hasLandscapeOrientation()
        );
    }

    private static CreateMediaConvertJobMessage getCreateMediaConvertJobMessageForVideo(
        MediaConvertInfo mediaConvertInfo,
        String sourceBucketPath
    ) {
        var job1UserMetadata = new HashMap<String, String>();
        job1UserMetadata.put("VideoId", mediaConvertInfo.videoId().toString());
        job1UserMetadata.put("ConversionType", "Video");
        job1UserMetadata.put("V", "2");
        return new CreateMediaConvertJobMessage(
            mediaConvertInfo.videoId(),
            mediaConvertInfo.mediaConvertJobRoleArn(),
            mediaConvertInfo.mediaConvertVideoJobTemplateName(),
            mediaConvertInfo.mediaConvertQueue(),
            job1UserMetadata,
            sourceBucketPath,
            normalizeS3Path(mediaConvertInfo.destinationBucketPath()),
            mediaConvertInfo.hasLandscapeOrientation()
        );
    }
}
