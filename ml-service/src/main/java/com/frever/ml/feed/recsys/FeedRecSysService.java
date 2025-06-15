package com.frever.ml.feed.recsys;

import static com.frever.ml.utils.Constants.NUM_FEED_RECOMMENDATIONS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.dao.SchoolTaskDao;
import com.frever.ml.dao.TemplateDao;
import com.frever.ml.dao.UserDao;
import com.frever.ml.dao.VideoDao;
import com.frever.ml.dto.CandidateVideo;
import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.GeoLocation;
import com.frever.ml.dto.RecommendedVideo;
import com.frever.ml.dto.RecommendedVideoResponse;
import com.frever.ml.dto.SongInfo;
import com.frever.ml.dto.UserInfo;
import com.frever.ml.dto.UserSoundInfo;
import com.frever.ml.feed.recsys.scoreStrategy.PersonalizedHeuristicsRecommendationResult;
import com.frever.ml.feed.recsys.scoreStrategy.ScoreTreatmentControl;
import com.frever.ml.feed.recsys.scoreStrategy.ScoreTreatmentEmphasizeFollowing;
import com.frever.ml.feed.recsys.scoreStrategy.ScoreTreatmentWithTwoCandidateLists;
import com.google.common.base.Stopwatch;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class FeedRecSysService {
    @Inject
    UserDao userDao;
    @Inject
    VideoDao videoDao;
    @Inject
    SchoolTaskDao schoolTaskDao;
    @Inject
    TemplateDao templateDao;
    @Inject
    GeoClusterService geoClusterService;
    @Inject
    ScoreTreatmentEmphasizeFollowing scoreTreatmentEmphasizeFollowing;
    @Inject
    ScoreTreatmentControl scoreTreatmentControl;
    @Inject
    ScoreTreatmentWithTwoCandidateLists scoreTreatmentWithTwoCandidateLists;
    @Inject
    ObjectMapper objectMapper;

    public List<RecommendedVideoResponse> getRecommendations(
        long groupId,
        Map<String, String> freverExperiment,
        GeoLocation geoLocation
    ) {
        var sw = Stopwatch.createStarted();
        var userInfo = userDao.getUserInfo(groupId);
        if (userInfo == null) {
            Log.warn("User info not found for group: " + groupId);
            return Collections.emptyList();
        }
        Log.info("Got user info for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var levelIds = schoolTaskDao.getSchoolTaskLevelIds();
        Log.info("Got Level-Ids for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var assignedGeoClusters = geoClusterService.assignedGeoClusters(userInfo);
        Log.info("Got GeoClusters for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var blockedUsers = userDao.getBlockedUsers(userInfo.groupId());
        Log.info("Got blocked users for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var localColdStartVideos =
            videoDao.getColdStartRecommendations(userInfo, levelIds, assignedGeoClusters.getFirst(), blockedUsers);
        Log.info("Got CS REC for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        var templateRankingVideos = templateDao.getVideosBasedOnTemplateRanking(
            userInfo,
            levelIds,
            assignedGeoClusters.getFirst(),
            blockedUsers,
            localColdStartVideos.size() + 1
        );
        Log.info("Got Template Rank Videos: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms, " +
            "num Template Rank Videos: " + templateRankingVideos.size());
        var videosBeforePersonalizedHeuristics =
            new ArrayList<RecommendedVideo>(localColdStartVideos.size() + templateRankingVideos.size());
        videosBeforePersonalizedHeuristics.addAll(localColdStartVideos);
        videosBeforePersonalizedHeuristics.addAll(templateRankingVideos);
        var personalizedHeuristicsRecommendations = getPersonalizedHeuristicsRecommendations(
            userInfo,
            levelIds,
            assignedGeoClusters,
            blockedUsers,
            freverExperiment,
            geoLocation,
            videosBeforePersonalizedHeuristics.size() + 1
        );
        Log.info("Got PH REC for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms, " +
            "num PH REC: " + personalizedHeuristicsRecommendations.recommendedVideos().size());
        var recommendedVideos = Objects.requireNonNull(personalizedHeuristicsRecommendations).recommendedVideos();
        List<RecommendedVideo> results = new ArrayList<>();
        results.addAll(videosBeforePersonalizedHeuristics);
        results.addAll(recommendedVideos);
        List<RecommendedVideo> deduplicatedResults = deduplicateBasedOnVideoId(results);
        if (!personalizedHeuristicsRecommendations.remainingVideos().isEmpty()) {
            deduplicatedResults.addAll(getRandomRecommendations(
                personalizedHeuristicsRecommendations.remainingVideos(),
                deduplicatedResults.size() + 1,
                NUM_FEED_RECOMMENDATIONS - deduplicatedResults.size()
            ));
        }
        Log.info("Got all REC for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms, " +
            "num REC: " + deduplicatedResults.size());
        List<RecommendedVideo> finalResults = deduplicateBasedOnVideoId(deduplicatedResults);
        try {
            return finalResults.stream().map(this::mapRecommendedVideoToResponse).toList();
        } finally {
            sw.stop();
            Log.info("Returning REC for group: " + groupId + " in " + sw.elapsed(TimeUnit.MILLISECONDS) + " ms, "
                + "num REC: " + finalResults.size());
        }
    }

    private static List<RecommendedVideo> deduplicateBasedOnVideoId(List<RecommendedVideo> videos) {
        List<RecommendedVideo> deduplicatedResults = new ArrayList<>();
        Set<Long> videoIds = new HashSet<>(videos.size());
        for (var video : videos) {
            if (!videoIds.contains(video.candidateVideo().videoId())) {
                deduplicatedResults.add(video);
                videoIds.add(video.candidateVideo().videoId());
            }
        }
        return deduplicatedResults;
    }

    private RecommendedVideoResponse mapRecommendedVideoToResponse(RecommendedVideo video) {
        long videoId = video.candidateVideo().videoId();
        long videoGroupId = video.candidateVideo().groupId();
        List<Long> externalSongIds = video.candidateVideo().externalSongIds();
        UserSoundInfo[] userSoundInfo;
        try {
            userSoundInfo = objectMapper.readValue(video.candidateVideo().userSoundInfo(), UserSoundInfo[].class);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to parse UserSoundInfo for VideoId %s", videoId, e);
            return null;
        }
        SongInfo[] songInfo;
        try {
            songInfo = objectMapper.readValue(video.candidateVideo().songInfo(), SongInfo[].class);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to parse SongInfo for VideoId %s", videoId, e);
            return null;
        }
        String source = video.source();
        return new RecommendedVideoResponse(
            videoId,
            videoGroupId,
            externalSongIds,
            userSoundInfo,
            songInfo,
            source
        );
    }

    private List<RecommendedVideo> getRandomRecommendations(
        List<CandidateVideo> candidates,
        int startingRank,
        int num
    ) {
        if (num >= candidates.size()) {
            int[] index = new int[]{startingRank};
            return candidates.stream()
                .map(candidate -> new RecommendedVideo(candidate, index[0]++, "Random"))
                .toList();
        }
        int[] unique = new Random().ints(0, candidates.size())
            .distinct()
            .limit(num)
            .sorted()
            .toArray();
        List<RecommendedVideo> results = new ArrayList<>();
        for (var i : unique) {
            results.add(new RecommendedVideo(candidates.get(i), startingRank + i, "Random"));
        }
        return results;
    }

    public List<Long> getPersonalizedHeuristicsVideoRecommendation(
        UserInfo userInfo,
        List<Long> levelIds,
        List<GeoCluster> assignedGeoClusters,
        List<Long> blockedUsers
    ) {
        var personalizedHeuristicsRecommendations = getPersonalizedHeuristicsRecommendations(
            userInfo, levelIds, assignedGeoClusters, blockedUsers, Collections.emptyMap(), null, 0
        );
        return personalizedHeuristicsRecommendations.recommendedVideos().stream().map(
            v -> v.candidateVideo().videoId()
        ).toList();
    }

    private PersonalizedHeuristicsRecommendationResult getPersonalizedHeuristicsRecommendations(
        UserInfo userInfo,
        List<Long> levelIds,
        List<GeoCluster> assignedGeoClusters,
        List<Long> blockedUsers,
        Map<String, String> freverExperiment,
        GeoLocation geoLocation,
        int startRank
    ) {
        if (shouldCalculateScoreTreatmentEmphasizeFollowing(freverExperiment)) {
            return scoreTreatmentEmphasizeFollowing.getPersonalizedHeuristicsRecommendations(
                userInfo, levelIds, assignedGeoClusters, blockedUsers, startRank
            );
        } else if (shouldCalculateScoreOriginal(freverExperiment)) {
            return scoreTreatmentControl.getPersonalizedHeuristicsRecommendations(
                userInfo, levelIds, assignedGeoClusters, blockedUsers, startRank
            );
        }
        return scoreTreatmentWithTwoCandidateLists.getPersonalizedHeuristicsRecommendations(
            userInfo, levelIds, assignedGeoClusters, blockedUsers, geoLocation, startRank
        );
    }

    private static boolean shouldCalculateScoreTreatmentEmphasizeFollowing(Map<String, String> freverExperiment) {
        return freverExperiment.containsKey("ml_feed_following_2") && freverExperiment.get("ml_feed_following_2")
            .equals("treatment");
    }

    private static boolean shouldCalculateScoreOriginal(Map<String, String> freverExperiment) {
        return freverExperiment.containsKey("ml_feed_following_2") && freverExperiment.get("ml_feed_following_2")
            .equals("control");
    }
}
