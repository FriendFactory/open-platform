package com.frever.ml.feed.recsys.scoreStrategy;

import static com.frever.ml.utils.Constants.MAX_CONSECUTIVE_VIDEOS;
import static com.frever.ml.utils.Constants.NUM_DAYS_LIKED_VIDEO_CHECK;
import static com.frever.ml.utils.Constants.NUM_FEED_PERSONALIZED;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toSet;

import com.frever.ml.dao.CrewDao;
import com.frever.ml.dao.UserDao;
import com.frever.ml.dao.VideoDao;
import com.frever.ml.dto.CandidateVideo;
import com.frever.ml.dto.CandidateVideoWithDistanceLevel.DistanceLevel;
import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.GeoLocation;
import com.frever.ml.dto.LikedAccount;
import com.frever.ml.dto.UserInfo;
import com.frever.ml.dto.VideoIdAndDistance;
import com.frever.ml.dto.VideoInfo;
import com.frever.ml.dto.CandidateVideoWithDistanceLevel;
import com.frever.ml.utils.AsyncServiceUsingOwnThreadPool;
import com.google.common.base.Stopwatch;
import com.google.common.collect.EvictingQueue;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <a href="https://friendfactory.atlassian.net/wiki/spaces/FFTS/pages/1639710726/Split+Extraction+of+Gross+Video+List+2024-05-20">Reference</a>
 */
@ApplicationScoped
public class ScoreTreatmentWithTwoCandidateLists extends AsyncServiceUsingOwnThreadPool {
    // probability map for following list
    private static final Map<Integer, Double> PROBABILITY_MAP;
    private static final Map<DistanceLevel, Double> DISTANCE_LEVEL_SCORE_MAP =
        Map.ofEntries(
            entry(DistanceLevel.Level1, 1d),
            entry(DistanceLevel.Level2, 0.9),
            entry(DistanceLevel.Level3, 0.8),
            entry(DistanceLevel.Level4, 0.7),
            entry(DistanceLevel.Level5, 0.6),
            entry(DistanceLevel.Level6, 0.5),
            entry(DistanceLevel.Level7, 0.4),
            entry(DistanceLevel.Level8, 0.3),
            entry(DistanceLevel.Level9, 0.2),
            entry(DistanceLevel.Level10, 0.1),
            entry(DistanceLevel.Level11, 0d)
        );

    @Inject
    VideoDao videoDao;
    @Inject
    CrewDao crewDao;
    @Inject
    UserDao userDao;

    static {
        Map<Integer, Double> temp = new HashMap<>(512, 1);
        initiateProbabilityMap(temp);
        PROBABILITY_MAP = Collections.unmodifiableMap(temp);
    }

    private static void initiateProbabilityMap(Map<Integer, Double> map) {
        map.put(1, 1d);
        for (int i = 2; i <= 10; i++) {
            map.put(i, 0.60);
        }
        for (int i = 11; i <= 20; i++) {
            map.put(i, 0.58);
        }
        for (int i = 21; i <= 30; i++) {
            map.put(i, 0.56);
        }
        for (int i = 31; i <= 40; i++) {
            map.put(i, 0.54);
        }
        for (int i = 41; i <= 50; i++) {
            map.put(i, 0.52);
        }
        for (int i = 51; i <= 500; i++) {
            map.put(i, 0.50);
        }
    }

    protected List<List<CandidateVideo>> getCandidateVideosForRanking(
        UserInfo userInfo,
        List<Long> levelIds,
        List<GeoCluster> assignedGeoClusters,
        List<Long> blockedUsers
    ) {
        Future<List<CandidateVideo>> future = executor.submit(() -> videoDao.getNonColdStartVideos(
            userInfo,
            levelIds,
            assignedGeoClusters,
            blockedUsers
        ));
        var geoCluster = assignedGeoClusters.getFirst();
        List<VideoInfo> candidates = videoDao.getFollowingVideos(userInfo, levelIds, blockedUsers);
        var followingVideos = new ArrayList<CandidateVideo>();
        for (VideoInfo video : candidates) {
            followingVideos.add(new CandidateVideo(video, geoCluster.priority()));
        }
        List<CandidateVideo> original = Collections.emptyList();
        try {
            original = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.warn("Failed to get candidate videos for ranking for groupId: " + userInfo.groupId(), e);
            future.cancel(true);
        }
        return List.of(followingVideos, original);
    }

    public PersonalizedHeuristicsRecommendationResult getPersonalizedHeuristicsRecommendations(
        UserInfo userInfo,
        List<Long> levelIds,
        List<GeoCluster> assignedGeoClusters,
        List<Long> blockedUsers,
        GeoLocation geoLocation,
        int startRank
    ) {
        var sw = Stopwatch.createStarted();
        var crewMembers = crewDao.getCrewMembers(userInfo.groupId());
        var likedAccounts = videoDao.getLikedAccounts(userInfo.groupId(), NUM_DAYS_LIKED_VIDEO_CHECK);
        Map<Long, Double> likeCountWeightMap = getLikeCountWeightMap(likedAccounts);
        var followInfo = userDao.getFollowInfo(userInfo.groupId());
        Log.info("Prepare Data for: " + userInfo.groupId() + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var candidateVideos = getCandidateVideosForRanking(userInfo, levelIds, assignedGeoClusters, blockedUsers);
        Log.info("Generate Videos for: " + userInfo.groupId() + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var newVideoList = getNewVideoList(candidateVideos.getLast(), likeCountWeightMap, geoLocation);
        var followingVideoList = getFollowingVideoList(
            candidateVideos.getFirst(),
            followInfo,
            crewMembers,
            likeCountWeightMap
        );
        var finalList = mergeNewVideoListAndFollowingVideoList(newVideoList, followingVideoList);
        var chunked = removeConsecutiveVideosAndCutOff(finalList);
        return ScoreStrategy.prepareRankForPersonalizedHeuristicsRecommendation(startRank, chunked, finalList);
    }

    private List<PersonalizedHeuristicsRecommendation> removeConsecutiveVideosAndCutOff(List<PersonalizedHeuristicsRecommendation> videos) {
        List<PersonalizedHeuristicsRecommendation> result = new ArrayList<>(NUM_FEED_PERSONALIZED);
        Queue<Long> seenGroupIds = EvictingQueue.create(MAX_CONSECUTIVE_VIDEOS);
        Queue<PersonalizedHeuristicsRecommendation> consecutiveVideos = new LinkedList<>();
        Set<Long> seenVideoIds = new HashSet<>(NUM_FEED_PERSONALIZED);
        for (var video : videos) {
            if (!seenGroupIds.contains(video.candidateVideo().groupId())
                && !seenVideoIds.contains(video.candidateVideo().videoId())) {
                seenGroupIds.add(video.candidateVideo().groupId());
                seenVideoIds.add(video.candidateVideo().videoId());
                result.add(video);
            } else if (seenGroupIds.contains(video.candidateVideo().groupId())) {
                consecutiveVideos.offer(video);
            }
            if (result.size() >= NUM_FEED_PERSONALIZED) {
                break;
            }
            for (int i = 0, size = consecutiveVideos.size(); i < size; i++) {
                var consecutiveVideo = consecutiveVideos.peek();
                if (!seenGroupIds.contains(Objects.requireNonNull(consecutiveVideo).candidateVideo().groupId())
                    && !seenVideoIds.contains(consecutiveVideo.candidateVideo().videoId())) {
                    result.add(consecutiveVideo);
                    seenGroupIds.add(consecutiveVideo.candidateVideo().groupId());
                    seenVideoIds.add(video.candidateVideo().videoId());
                    consecutiveVideos.poll();
                }
                if (result.size() >= NUM_FEED_PERSONALIZED) {
                    break;
                }
            }
        }
        return result;
    }

    private static List<PersonalizedHeuristicsRecommendation> mergeNewVideoListAndFollowingVideoList(
        List<PersonalizedHeuristicsRecommendation> newVideoList,
        List<PersonalizedHeuristicsRecommendation> followingVideoList
    ) {
        var random = new Random();
        var mergedList = new ArrayList<PersonalizedHeuristicsRecommendation>(newVideoList.size());
        var seenVideos = new HashSet<Long>();
        int iteration = Math.min(newVideoList.size(), followingVideoList.size());
        for (int i = 0; i < iteration; i++) {
            var followingVideo = followingVideoList.get(i);
            var newVideo = newVideoList.get(i);
            if (seenVideos.contains(newVideo.candidateVideo().videoId())
                && !seenVideos.contains(followingVideo.candidateVideo().videoId())) {
                mergedList.add(followingVideo);
                seenVideos.add(followingVideo.candidateVideo().videoId());
                continue;
            } else if (seenVideos.contains(followingVideo.candidateVideo().videoId())
                && !seenVideos.contains(newVideo.candidateVideo().videoId())) {
                mergedList.add(newVideo);
                seenVideos.add(newVideo.candidateVideo().videoId());
                continue;
            } else if (seenVideos.contains(newVideo.candidateVideo().videoId())
                && seenVideos.contains(followingVideo.candidateVideo().videoId())) {
                continue;
            }
            double probability = PROBABILITY_MAP.get(i + 1);
            if (random.nextDouble(0, 1) > probability) {
                mergedList.add(newVideo);
                seenVideos.add(newVideo.candidateVideo().videoId());
            } else {
                mergedList.add(followingVideo);
                seenVideos.add(followingVideo.candidateVideo().videoId());
            }
        }
        Set<Long> existingVideoIds = mergedList.stream().map(rec -> rec.candidateVideo().videoId()).collect(toSet());
        for (var video : followingVideoList) {
            if (existingVideoIds.contains(video.candidateVideo().videoId())) {
                continue;
            }
            mergedList.add(video);
            existingVideoIds.add(video.candidateVideo().videoId());
        }
        for (var video : newVideoList) {
            if (existingVideoIds.contains(video.candidateVideo().videoId())) {
                continue;
            }
            mergedList.add(video);
            existingVideoIds.add(video.candidateVideo().videoId());
        }
        return mergedList;
    }

    private static Map<Long, Double> getLikeCountWeightMap(List<LikedAccount> likedAccounts) {
        long maxLikeCount =
            likedAccounts.stream().map(LikedAccount::likeCount).max(Comparator.naturalOrder()).orElse(0L);
        return likedAccounts.stream()
            .collect(Collectors.toMap(
                LikedAccount::groupId,
                v -> maxLikeCount == 0 ? 0 : v.likeCount() / (double) maxLikeCount,
                Math::max
            ));
    }

    private static List<PersonalizedHeuristicsRecommendation> getFollowingVideoList(
        List<CandidateVideo> originalFollowingVideos,
        Map<Long, Boolean> followInfo,
        Set<Long> crewMembers,
        Map<Long, Double> likeCountWeightMap
    ) {
        var lastedVideoTime = originalFollowingVideos.stream()
            .map(CandidateVideo::createdTime)
            .max(Comparator.naturalOrder())
            .orElse(Instant.now());
        var personalizedHeuristicsRecommendations = new ArrayList<PersonalizedHeuristicsRecommendation>();
        for (var video : originalFollowingVideos) {
            int crewMember = crewMembers.contains(video.groupId()) ? 1 : 0;
            double likeCountWeight = likeCountWeightMap.getOrDefault(video.groupId(), 0d);
            int twoDirectionalFollow = followInfo.getOrDefault(video.groupId(), false) ? 1 : 0;
            double finalScore = ScoreTreatmentWithFollowingListCalculator.calculateScoreForVideoInFollowingList(
                video,
                likeCountWeight,
                twoDirectionalFollow,
                crewMember,
                lastedVideoTime
            );
            personalizedHeuristicsRecommendations.add(new PersonalizedHeuristicsRecommendation(video, finalScore));
        }
        personalizedHeuristicsRecommendations.sort(ScoreStrategy.PersonalizedHeuristicsRecommendationsComparator.INSTANCE.reversed());
        return personalizedHeuristicsRecommendations;
    }

    private List<PersonalizedHeuristicsRecommendation> getNewVideoList(
        List<CandidateVideo> originalNewVideoList,
        Map<Long, Double> likeCountWeightMap,
        GeoLocation geoLocation
    ) {
        var lastedVideoTime = originalNewVideoList.stream()
            .map(CandidateVideo::createdTime)
            .max(Comparator.naturalOrder())
            .orElse(Instant.now());
        Map<Long, DistanceLevel> videoIdToDistanceLevel;
        if (geoLocation != null) {
            List<Long> videoIds = originalNewVideoList.stream().map(CandidateVideo::videoId).toList();
            List<VideoIdAndDistance> videoDistanceInfo = videoDao.getVideoDistanceInfo(videoIds, geoLocation);
            videoIdToDistanceLevel = videoDistanceInfo.stream()
                .collect(
                    Collectors.toMap(
                        VideoIdAndDistance::videoId,
                        vd -> DistanceLevel.getDistanceLevel(vd.distance())
                    )
                );
        } else {
            videoIdToDistanceLevel = Collections.emptyMap();
        }
        List<CandidateVideoWithDistanceLevel> candidateVideoWithDistanceLevels = originalNewVideoList.stream()
            .map(v -> new CandidateVideoWithDistanceLevel(
                v,
                videoIdToDistanceLevel.getOrDefault(v.videoId(), DistanceLevel.Level11)
            ))
            .toList();
        var personalizedHeuristicsRecommendations = new ArrayList<PersonalizedHeuristicsRecommendation>();
        for (var video : candidateVideoWithDistanceLevels) {
            double likeCountWeight = likeCountWeightMap.getOrDefault(video.groupId(), 0d);
            double finalScore =
                ScoreTreatmentWithFollowingListCalculator.calculateScoreForVideoInNewList(
                    video,
                    likeCountWeight,
                    lastedVideoTime
                );
            personalizedHeuristicsRecommendations.add(new PersonalizedHeuristicsRecommendation(
                video.candidateVideo(),
                finalScore
            ));
        }
        personalizedHeuristicsRecommendations.sort(ScoreStrategy.PersonalizedHeuristicsRecommendationsComparator.INSTANCE.reversed());
        return personalizedHeuristicsRecommendations;
    }

    protected static class ScoreTreatmentWithFollowingListCalculator {
        // https://friendfactory.atlassian.net/wiki/spaces/FFTS/pages/1639710726/Split+Extraction+of+Gross+Video+List+2024-05-20
        private static final double TIME_SCORE = 0.7;
        private static final double LIKE_SCORE = 0.5;
        private static final double FRIENDS_SCORE = 0.4;
        private static final double CREW_MEMBER_SCORE = 0.2;
        private static final double FRIENDSHIP_SCORE = 0.3;
        // https://friendfactory.atlassian.net/wiki/spaces/FFTS/pages/1574076417/Use+Geographical+Location+in+Ranking+2024-06-10
        private static final double GEO_LOCATION_SCORE = 0.6;
        // https://friendfactory.atlassian.net/wiki/spaces/FFTS/pages/1710686209/Up-Rank+Connections+Templates+2024-07-02
        private static final double TEMPLATE_SCORE = 0.6;

        protected static double calculateScoreForVideoInNewList(
            CandidateVideoWithDistanceLevel video,
            double likeCountWeight,
            Instant lastedVideoTime
        ) {
            double likeScore = likeCountWeight * LIKE_SCORE;
            double timeScore =
                TIME_SCORE * (double) video.createdTime().getEpochSecond() / lastedVideoTime.getEpochSecond();
            double geoLocationScore = DISTANCE_LEVEL_SCORE_MAP.get(video.distanceLevel()) * GEO_LOCATION_SCORE;
            return timeScore + likeScore + geoLocationScore;
        }

        protected static double calculateScoreForVideoInFollowingList(
            CandidateVideo video,
            double likeCountWeight,
            int twoDirectionalFollow,
            int crewMember,
            Instant lastedVideoTime
        ) {
            double friendsScore = FRIENDS_SCORE * twoDirectionalFollow;
            double crewMemberScore = CREW_MEMBER_SCORE * crewMember;
            double friendshipScore = FRIENDSHIP_SCORE * (crewMemberScore + friendsScore);
            double likeScore = likeCountWeight * LIKE_SCORE;
            double timeScore =
                TIME_SCORE * (double) video.createdTime().getEpochSecond() / lastedVideoTime.getEpochSecond();
            if (video.generatedTemplateId() != null) {
                return timeScore + likeScore + friendshipScore + TEMPLATE_SCORE;
            } else {
                return timeScore + likeScore + friendshipScore;
            }
        }
    }

    @Override
    protected int getQueueSize() {
        return 0;
    }
}
