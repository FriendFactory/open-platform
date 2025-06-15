package com.frever.ml.template.recsys;

import com.frever.ml.utils.AbstractResponseUploadService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TemplateRecommendationResponseUploadService extends AbstractResponseUploadService {
    private static final String KEY_PREFIX = "template-recsys/responses/";

    public void uploadRecommendationResponseToS3(long groupId, TemplateRecommendationResponse recommendation) {
        uploadResponseToS3(
            groupId,
            KEY_PREFIX,
            recommendation,
            "Uploaded template recommendations for group: " + groupId + " to S3"
        );
    }
}
