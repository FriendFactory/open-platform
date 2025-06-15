package com.frever.ml.dto;

public record RecommendedVideo(CandidateVideo candidateVideo, int order, String source) {
}
