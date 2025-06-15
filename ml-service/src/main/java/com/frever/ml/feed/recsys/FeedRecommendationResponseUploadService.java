package com.frever.ml.feed.recsys;

import com.frever.ml.dto.RecommendedVideoResponse;
import com.frever.ml.utils.AbstractResponseUploadService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class FeedRecommendationResponseUploadService extends AbstractResponseUploadService {
    private static final String KEY_PREFIX = "feed-recsys/responses/";

    public void uploadResponseToS3(long groupId, List<RecommendedVideoResponse> recommendations) {
        uploadResponseToS3(
            groupId,
            KEY_PREFIX,
            recommendations,
            "Uploaded video feed recommendations for group: " + groupId + " to S3"
        );
    }
}
