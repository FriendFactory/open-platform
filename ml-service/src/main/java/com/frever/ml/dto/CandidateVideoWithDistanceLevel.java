package com.frever.ml.dto;

import java.time.Instant;

public record CandidateVideoWithDistanceLevel(CandidateVideo candidateVideo, DistanceLevel distanceLevel) {
    public enum DistanceLevel {
        Level1(49),
        Level2(99),
        Level3(149),
        Level4(199),
        Level5(249),
        Level6(299),
        Level7(349),
        Level8(399),
        Level9(449),
        Level10(499),
        Level11(Integer.MAX_VALUE);
        final double threshold;

        DistanceLevel(double threshold) {
            this.threshold = threshold;
        }

        public static DistanceLevel getDistanceLevel(double distance) {
            for (DistanceLevel level : DistanceLevel.values()) {
                if (distance <= level.threshold) {
                    return level;
                }
            }
            return Level11;
        }
    }

    public long groupId() {
        return candidateVideo.groupId();
    }

    public Instant createdTime() {
        return candidateVideo.createdTime();
    }
}
