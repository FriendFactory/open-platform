package com.frever.platform.timers.followerStats;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.frever.platform.timers.followerStats.entities.FollowEdge;
import com.frever.platform.timers.messaging.GroupUnfollowedMessage;
import com.frever.platform.timers.utils.AbstractAggregationService;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Transactional
public class FollowEdgeAggregationService extends AbstractAggregationService {
    public static final String TIMER_NAME = "follower-recommendation-aggregation";

    private static final String FOLLOW_EDGE_LIST = """
        INSERT INTO stats.follow_edge (source, destination, is_mutual, source_is_minor, source_strict_coppa_rules, destination_is_minor, destination_strict_coppa_rules, source_latest_login, destination_latest_login)
        SELECT
            f."FollowerId" AS source,
            f."FollowingId" AS destination,
            f."IsMutual" AS is_mutual,
            src_g."IsMinor" AS source_is_minor,
            src_c."StrictCoppaRules" As source_strict_coppa_rules,
            dst_g."IsMinor" AS destination_is_minor,
            dst_c."StrictCoppaRules" As destination_strict_coppa_rules,
            ua1."OccurredAt" AS source_latest_login,
            ua2."OccurredAt" AS destination_latest_login
        FROM "Follower" f
        LEFT JOIN (
            SELECT "GroupId", MAX("OccurredAt") AS "OccurredAt"
            FROM "UserActivity"
            WHERE "ActionType" = 'Login'
            GROUP BY "GroupId"
        ) ua1 ON f."FollowerId" = ua1."GroupId"
        LEFT JOIN (
            SELECT "GroupId", MAX("OccurredAt") AS "OccurredAt"
            FROM "UserActivity"
            WHERE "ActionType" = 'Login'
            GROUP BY "GroupId"
        ) ua2 ON f."FollowingId" = ua2."GroupId"
        INNER JOIN "Group" src_g on src_g."Id" = f."FollowerId" AND src_g."IsBlocked" = false AND src_g."DeletedAt" IS NULL
        INNER JOIN "Country" src_c on src_c."Id" = src_g."TaxationCountryId"
        INNER JOIN "Group" dst_g on dst_g."Id" = f."FollowingId" AND dst_g."IsBlocked" = false AND dst_g."DeletedAt" IS NULL
        INNER JOIN "Country" dst_c on dst_c."Id" = dst_g."TaxationCountryId"
        WHERE f."FollowerId" != 742
          AND f."FollowingId" != 742
          AND ua1."OccurredAt" >= now() - INTERVAL '2 weeks'
          AND ua2."OccurredAt" >= now() - INTERVAL '2 weeks'
        """;

    private static final String EDGE_LIST_GROUP_INFO = """
        SELECT
            g."Id" as group_id,
            g."IsMinor" AS is_minor,
            c."StrictCoppaRules" As strict_coppa_rules
            FROM "Group" g
        INNER JOIN "Country" c on g."TaxationCountryId" = c."Id"
        WHERE g."IsBlocked" = false AND g."DeletedAt" IS NULL and g."Id" in (:groupIds)
        GROUP BY g."Id", g."IsMinor", c."StrictCoppaRules"
        """;

    @Override
    protected String getTimerName() {
        return TIMER_NAME;
    }

    @Lock(value = Lock.Type.WRITE, time = 1, unit = TimeUnit.MINUTES)
    public void bootstrap() {
        Log.info("Bootstrapping data in 'stats' schema for follow_edge_list.");
        recordTimerExecution();
        cleanup();
        bootstrapFollowEdgeList();
        Log.info("Done bootstrapping data in 'stats' schema for follow_edge_list.");
    }

    private void bootstrapFollowEdgeList() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            int i = statement.executeUpdate(FOLLOW_EDGE_LIST);
            Log.infof("Inserted %s rows into follow_edge_list table in 'stats' schema.", i);
        } catch (SQLException e) {
            String message = "Failed to bootstrap follow_edge_list table in 'stats' schema.";
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private void cleanup() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate table stats.follow_edge");
        } catch (SQLException e) {
            String message = "Failed to cleanup follow_edge_list table in 'stats' schema.";
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    public void aggregateFollowEdgeList(List<Follower> newFollowers) {
        List<Long> followerIds = newFollowers.stream().map(Follower::followerId).toList();
        List<Long> followingIds = newFollowers.stream().map(Follower::followingId).toList();
        List<Long> allGroupIds = new ArrayList<>(followerIds);
        allGroupIds.addAll(followingIds);
        @SuppressWarnings("unchecked")
        List<GroupInfo> groupInfo = entityManager.createNativeQuery(EDGE_LIST_GROUP_INFO, GroupInfo.class)
            .setParameter("groupIds", allGroupIds)
            .getResultList();
        var groupInfoMap = groupInfo.stream().collect(toMap(GroupInfo::groupId, identity()));
        for (Follower follower : newFollowers) {
            GroupInfo sourceInfo = groupInfoMap.get(follower.followerId());
            GroupInfo destInfo = groupInfoMap.get(follower.followingId());
            if (sourceInfo == null || destInfo == null) {
                Log.warnf(
                    "Group info not found for followerId: %s or followingId: %s",
                    follower.followerId(),
                    follower.followingId()
                );
                continue;
            }
            entityManager.merge(getFollowEdge(follower, sourceInfo, destInfo));
        }
    }

    private static FollowEdge getFollowEdge(Follower follower, GroupInfo sourceInfo, GroupInfo destInfo) {
        FollowEdge edge = new FollowEdge();
        edge.setSource(follower.followerId());
        edge.setDestination(follower.followingId());
        edge.setMutual(follower.isMutual());
        edge.setSourceIsMinor(sourceInfo.isMinor());
        edge.setSourceStrictCoppaRules(sourceInfo.strictCoppaRules());
        edge.setDestinationIsMinor(destInfo.isMinor());
        edge.setDestinationStrictCoppaRules(destInfo.strictCoppaRules());
        return edge;
    }

    public void handleGroupDeleteMessage(long groupId) {
        Log.infof("Deleting follow edges for group %s", groupId);
        int i = entityManager.createQuery("DELETE FROM FollowEdge WHERE source = :groupId OR destination = :groupId")
            .setParameter("groupId", groupId)
            .executeUpdate();
        Log.infof("Deleted %s follow edges for group deletion %s", i, groupId);
    }

    public void handleGroupUnfollowedMessage(GroupUnfollowedMessage groupUnfollowedMessage) {
        Log.infof("handle group unfollow for edge: %s", groupUnfollowedMessage);
        long source = groupUnfollowedMessage.followerId();
        long destination = groupUnfollowedMessage.followingId();
        boolean isMutual = groupUnfollowedMessage.isMutual();
        int i = entityManager.createQuery(
                "DELETE FROM FollowEdge WHERE source = :source AND destination = :destination")
            .setParameter("source", source).setParameter("destination", destination).executeUpdate();
        Log.infof("Deleted %s follow edges for unfollow.", i);
        if (isMutual) {
            int u = entityManager.createQuery(
                    "UPDATE FollowEdge SET isMutual = false WHERE source = :destination and destination = :source")
                .setParameter("source", source).setParameter("destination", destination).executeUpdate();
            Log.infof("Updated %s follow edges for unfollow because of isMutual.", u);
        }
    }
}
