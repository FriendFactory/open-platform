package com.frever.ml.follow.recsys;

import static com.frever.ml.utils.Constants.NUM_FOLLOW_RECOMMENDATIONS;
import static com.frever.ml.utils.Constants.NUM_FRIENDS_PERSONALIZED;
import static java.util.stream.Collectors.toMap;

import com.frever.ml.dao.FollowEdgeDao;
import com.frever.ml.dto.EgoNetwork;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FollowRecommendationService {
    @Inject
    FollowEdgeDao followEdgeDao;

    public ColdStart getColdStart(long groupId) {
        var mostInfluentialNodes = followEdgeDao.getMostInfluentialNodesOptimized(groupId);
        return new ColdStart(mostInfluentialNodes.stream()
            .map(node -> new ColdStart.ColdStartItem(node, List.of()))
            .toList());
    }

    public List<SecondHopFriends> getSecondHopFriends(long groupId) {
        return followEdgeDao.getSecondHopFriendsWithLimit(groupId);
    }

    public List<Long> getFollowBack(long groupId) {
        List<Long> followBack = followEdgeDao.getFollowBack(groupId);
        Log.infof("Group: %s, follow-back: %s", groupId, followBack);
        return followBack;
    }

    public EgoNetwork getEgoNetwork(long groupId) {
        var firstHopFriends = followEdgeDao.getFirstHopFriends(groupId);
        var firstHopFriendsList = new ArrayList<>(firstHopFriends.keySet());
        var mutualFriends =
            firstHopFriends.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        var secondHopFriends = followEdgeDao.getAllSecondHopFriends(groupId);
        return new EgoNetwork(
            new HashSet<>(firstHopFriendsList),
            new HashSet<>(secondHopFriends),
            new HashSet<>(mutualFriends)
        );
    }

    public FollowRecommendation getFollowRecommendation(long groupId) {
        var result = new ArrayList<FollowRecommendation.FollowRecommendationItem>();
        var seen = new HashSet<Long>();
        var followBack = commonFriendsRecommendations(groupId, seen, result);
        influentialRecommendations(groupId, seen, followBack, result);
        return new FollowRecommendation(result);
    }

    private void influentialRecommendations(
        long groupId,
        HashSet<Long> seen,
        HashSet<Long> followBack,
        ArrayList<FollowRecommendation.FollowRecommendationItem> result
    ) {
        int remaining = NUM_FOLLOW_RECOMMENDATIONS - Math.min(NUM_FRIENDS_PERSONALIZED, result.size());
        var mostInfluentialNodes = followEdgeDao.getMostInfluentialNodesOptimized(groupId);
        Log.infof(
            "Will pick %s influential nodes for group: %s, most-influential-nodes %s",
            remaining,
            groupId,
            mostInfluentialNodes
        );
        List<Long> candidateInfluentialFriends = new ArrayList<>(mostInfluentialNodes.stream()
            .filter(f -> !seen.contains(f) && !followBack.contains(f))
            .toList());
        Collections.shuffle(candidateInfluentialFriends);
        List<Long> influential;
        if (candidateInfluentialFriends.size() <= remaining) {
            influential = candidateInfluentialFriends;
        } else {
            influential = candidateInfluentialFriends.subList(0, remaining);
        }
        Log.infof("Group: %s, picked influential-nodes %s", groupId, influential);
        for (var group : influential) {
            var item = new FollowRecommendation.FollowRecommendationItem(group, "influential", List.of());
            result.add(item);
        }
    }

    private HashSet<Long> commonFriendsRecommendations(
        long groupId,
        HashSet<Long> seen,
        ArrayList<FollowRecommendation.FollowRecommendationItem> result
    ) {
        var secondHopFriends = followEdgeDao.getSecondHopFriendsWithLimit(groupId);
        Log.infof("Group: %s, second-hop-friends %s", groupId, secondHopFriends);
        var followBack = new HashSet<>(followEdgeDao.getFollowBack(groupId));
        Log.infof("Group: %s, follow-back %s", groupId, followBack);
        var candidateCommonFriends =
            secondHopFriends.stream().map(SecondHopFriends::groupId).filter(f -> !followBack.contains(f)).toList();
        var secondHopFriendsMap =
            secondHopFriends.stream().collect(toMap(SecondHopFriends::groupId, SecondHopFriends::commonFriends));
        for (var group : candidateCommonFriends) {
            if (seen.contains(group)) {
                continue;
            }
            var item = new FollowRecommendation.FollowRecommendationItem(group, "common_friends",
                secondHopFriendsMap.get(group)
            );
            result.add(item);
            seen.add(group);
            if (result.size() >= NUM_FRIENDS_PERSONALIZED) {
                break;
            }
        }
        return followBack;
    }
}
