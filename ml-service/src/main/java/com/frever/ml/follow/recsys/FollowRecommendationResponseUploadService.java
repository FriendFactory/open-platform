package com.frever.ml.follow.recsys;

import com.frever.ml.utils.AbstractResponseUploadService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FollowRecommendationResponseUploadService extends AbstractResponseUploadService {
    private static final String KEY_PREFIX_FOLLOW = "follow-recsys/follow/responses/";
    private static final String KEY_PREFIX_FOLLOW_BACK = "follow-recsys/follow-back/responses/";

    public void uploadRecommendationResponseToS3(long groupId, FollowRecommendation recommendation) {
        uploadResponseToS3(
            groupId,
            KEY_PREFIX_FOLLOW,
            recommendation,
            "Uploaded follow recommendations for group: " + groupId + " to S3"
        );
    }

    public void uploadFollowBackResponseToS3(long groupId, FollowBack followBack) {
        uploadResponseToS3(
            groupId,
            KEY_PREFIX_FOLLOW_BACK,
            followBack,
            "Uploaded follow-back response for group: " + groupId + " to S3"
        );
    }
}
