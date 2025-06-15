package com.frever.ml.feed.recsys.scoreStrategy;

import com.frever.ml.dto.CandidateVideo;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class ScoreTreatmentControl extends ScoreStrategy {
    private static final double FIRST_HOP_WEIGHT;
    private static final double SECOND_HOP_WEIGHT;
    private static final double CREW_MEMBER_WEIGHT;
    private static final double FRIENDSHIP_SCORE_WEIGHT;
    private static final double TIME_SCORE_WEIGHT;
    private static final double ACCOUNT_LIKE_SCORE_WEIGHT;

    static {
        // Friendship weights: FIRST_HOP=0.5 SECOND_HOP=0.2 CREW_MEMBER=0.5
        double firstHop = 0.5;
        double secondHop = 0.2;
        double crewMember = 0.5;
        double totalWeight = firstHop + secondHop + crewMember;
        FIRST_HOP_WEIGHT = firstHop / totalWeight;
        SECOND_HOP_WEIGHT = secondHop / totalWeight;
        CREW_MEMBER_WEIGHT = crewMember / totalWeight;
        // Weights: FRIENDSHIP=0.1 TIME=0.9 ACCOUNT_LIKE=0.5
        double friendship = 0.1;
        double time = 0.9;
        double accountLike = 0.5;
        double totalScoreWeight = friendship + time + accountLike;
        FRIENDSHIP_SCORE_WEIGHT = friendship / totalScoreWeight;
        TIME_SCORE_WEIGHT = time / totalScoreWeight;
        ACCOUNT_LIKE_SCORE_WEIGHT = accountLike / totalScoreWeight;
    }

    @Override
    protected double calculateScore(
        CandidateVideo video,
        Map<Long, Double> likeCountWeightMap,
        int firstHop,
        int secondHop,
        int crewMember,
        int friends,
        Instant lastedVideoTime
    ) {
        return ScoreTreatmentControlCalculator.calculate(
            video,
            likeCountWeightMap,
            firstHop,
            secondHop,
            crewMember,
            lastedVideoTime
        );
    }

    protected static class ScoreTreatmentControlCalculator {
        protected static double calculate(
            CandidateVideo video,
            Map<Long, Double> likeCountWeightMap,
            int firstHop,
            int secondHop,
            int crewMember,
            Instant lastedVideoTime
        ) {
            double likeCountWeight = likeCountWeightMap.getOrDefault(video.groupId(), 0d);
            double friendshipScore = firstHop * FIRST_HOP_WEIGHT + secondHop * SECOND_HOP_WEIGHT
                + crewMember * CREW_MEMBER_WEIGHT;
            double timeScore = (double) video.createdTime().getEpochSecond() / lastedVideoTime.getEpochSecond();
            return friendshipScore * FRIENDSHIP_SCORE_WEIGHT + timeScore * TIME_SCORE_WEIGHT
                + likeCountWeight * ACCOUNT_LIKE_SCORE_WEIGHT;
        }
    }
}
