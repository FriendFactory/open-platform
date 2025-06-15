package com.frever.ml.feed.recsys.scoreStrategy;

import static com.frever.ml.utils.Constants.MAX_CONSECUTIVE_VIDEOS;
import static com.frever.ml.utils.Constants.NUM_DAYS_LIKED_VIDEO_CHECK;
import static com.frever.ml.utils.Constants.NUM_FEED_PERSONALIZED;

import com.frever.ml.dao.CrewDao;
import com.frever.ml.dao.VideoDao;
import com.frever.ml.dto.CandidateVideo;
import com.frever.ml.dto.EgoNetwork;
import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.LikedAccount;
import com.frever.ml.dto.RecommendedVideo;
import com.frever.ml.dto.UserInfo;
import com.frever.ml.follow.recsys.FollowRecommendationService;
import com.frever.ml.utils.AsyncServiceUsingOwnThreadPool;
import com.google.common.base.Stopwatch;
import com.google.common.collect.EvictingQueue;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class ScoreStrategy extends AsyncServiceUsingOwnThreadPool {
    @Inject
    protected CrewDao crewDao;
    @Inject
    protected VideoDao videoDao;
    @Inject
    protected FollowRecommendationService followRecommendationService;

    public PersonalizedHeuristicsRecommendationResult getPersonalizedHeuristicsRecommendations(
        UserInfo userInfo,
        List<Long> levelIds,
        List<GeoCluster> assignedGeoClusters,
        List<Long> blockedUsers,
        int startRank
    ) {
        var sw = Stopwatch.createStarted();
        Future<EgoNetwork> egoNetworkFuture = executor.submit(() -> followRecommendationService.getEgoNetwork(userInfo.groupId()));
        var crewMembers = crewDao.getCrewMembers(userInfo.groupId());
        Log.info("Crew for group: " + userInfo.groupId() + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var likedAccounts = videoDao.getLikedAccounts(userInfo.groupId(), NUM_DAYS_LIKED_VIDEO_CHECK);
        Log.info("Liked for group: " + userInfo.groupId() + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var nonColdStartVideos = videoDao.getNonColdStartVideos(userInfo, levelIds, assignedGeoClusters, blockedUsers);
        Log.info("NCS videos for group: " + userInfo.groupId() + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        long maxLikeCount =
            likedAccounts.stream().map(LikedAccount::likeCount).max(Comparator.naturalOrder()).orElse(0L);
        Map<Long, Double> likeCountWeightMap = likedAccounts.stream()
            .collect(Collectors.toMap(
                LikedAccount::groupId,
                v -> maxLikeCount == 0 ? 0 : v.likeCount() / (double) maxLikeCount,
                Math::max
            ));
        var lastedVideoTime = nonColdStartVideos.stream()
            .map(CandidateVideo::createdTime)
            .max(Comparator.naturalOrder())
            .orElse(Instant.now());
        EgoNetwork egoNetwork;
        try {
            egoNetwork = egoNetworkFuture.get(5, TimeUnit.SECONDS);
            Log.info("Ego-N for group: " + userInfo.groupId() + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        } catch (Exception e) {
            Log.warn("Failed to get ego network for user group: " + userInfo.groupId(), e);
            egoNetwork = EgoNetwork.empty();
        }
        var personalizedHeuristicsRecommendations = new ArrayList<PersonalizedHeuristicsRecommendation>();
        for (var video : nonColdStartVideos) {
            int firstHop = egoNetwork.firstHop().contains(video.groupId()) ? 1 : 0;
            int secondHop = egoNetwork.secondHop().contains(video.groupId()) ? 1 : 0;
            int friends = egoNetwork.mutual().contains(video.groupId()) ? 1 : 0;
            int crewMember = crewMembers.contains(video.groupId()) ? 1 : 0;
            double finalScore =
                calculateScore(video, likeCountWeightMap, firstHop, secondHop, crewMember, friends, lastedVideoTime);
            personalizedHeuristicsRecommendations.add(new PersonalizedHeuristicsRecommendation(video, finalScore));
        }
        personalizedHeuristicsRecommendations.sort(PersonalizedHeuristicsRecommendationsComparator.INSTANCE.reversed());
        var chunked = removeConsecutiveVideosAndCutOff(personalizedHeuristicsRecommendations);
        return prepareRankForPersonalizedHeuristicsRecommendation(
            startRank,
            chunked,
            personalizedHeuristicsRecommendations
        );
    }

    protected static PersonalizedHeuristicsRecommendationResult prepareRankForPersonalizedHeuristicsRecommendation(
        int startRank,
        List<PersonalizedHeuristicsRecommendation> chunked,
        List<PersonalizedHeuristicsRecommendation> all
    ) {
        Map<Long, PersonalizedHeuristicsRecommendation> chunkedVideoIdToRecommendation =
            chunked.stream().collect(Collectors.toMap((v) -> v.candidateVideo().videoId(), v -> v));
        List<RecommendedVideo> recommended = new ArrayList<>(all.size());
        int order = startRank;
        for (var v : chunked) {
            recommended.add(new RecommendedVideo(v.candidateVideo(), order++, "Personalized"));
        }
        var seenVideoIds = new HashSet<>(all.size());
        List<CandidateVideo> remainingVideos = all.stream()
            .map(PersonalizedHeuristicsRecommendation::candidateVideo)
            .filter(candidateVideo -> !chunkedVideoIdToRecommendation.containsKey(candidateVideo.videoId()))
            .filter(candidateVideo -> seenVideoIds.add(candidateVideo.videoId()))
            .toList();
        return new PersonalizedHeuristicsRecommendationResult(recommended, remainingVideos);
    }

    protected abstract double calculateScore(
        CandidateVideo video,
        Map<Long, Double> likeCountWeightMap,
        int firstHop,
        int secondHop,
        int crewMember,
        int friends,
        Instant lastedVideoTime
    );

    private List<PersonalizedHeuristicsRecommendation> removeConsecutiveVideosAndCutOff(List<PersonalizedHeuristicsRecommendation> videos) {
        List<PersonalizedHeuristicsRecommendation> result = new ArrayList<>(NUM_FEED_PERSONALIZED);
        Queue<Long> seenGroupIds = EvictingQueue.create(MAX_CONSECUTIVE_VIDEOS);
        Set<Long> seenVideoIds = new HashSet<>(NUM_FEED_PERSONALIZED);
        for (var video : videos) {
            if (!seenGroupIds.contains(video.candidateVideo().groupId())
                && !seenVideoIds.contains(video.candidateVideo().videoId())) {
                seenGroupIds.add(video.candidateVideo().groupId());
                seenVideoIds.add(video.candidateVideo().videoId());
                result.add(video);
            }
            if (result.size() >= NUM_FEED_PERSONALIZED) {
                break;
            }
        }
        return result;
    }

    protected static class PersonalizedHeuristicsRecommendationsComparator
        implements Comparator<PersonalizedHeuristicsRecommendation> {
        static final PersonalizedHeuristicsRecommendationsComparator INSTANCE =
            new PersonalizedHeuristicsRecommendationsComparator();

        @Override
        public int compare(PersonalizedHeuristicsRecommendation p1, PersonalizedHeuristicsRecommendation p2) {
            int geoClusterPriority =
                Integer.compare(p1.candidateVideo().geoClusterPriority(), p2.candidateVideo().geoClusterPriority());
            if (geoClusterPriority != 0) {
                return geoClusterPriority;
            }
            return Double.compare(p1.score(), p2.score());
        }
    }
}
