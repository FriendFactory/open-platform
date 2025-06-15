package com.frever.ml.feed.recsys.scoreStrategy;

import com.frever.ml.dto.CandidateVideo;
import com.frever.ml.dto.RecommendedVideo;
import java.util.List;

public record PersonalizedHeuristicsRecommendationResult(List<RecommendedVideo> recommendedVideos,
                                                         List<CandidateVideo> remainingVideos) {
}
