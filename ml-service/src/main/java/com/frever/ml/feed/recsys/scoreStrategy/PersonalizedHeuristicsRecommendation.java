package com.frever.ml.feed.recsys.scoreStrategy;

import com.frever.ml.dto.CandidateVideo;

public record PersonalizedHeuristicsRecommendation(CandidateVideo candidateVideo, double score) {
}