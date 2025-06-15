package com.frever.platform.timers.followerStats;

import com.frever.platform.timers.followerStats.entities.FollowerStats;
import com.frever.platform.timers.messaging.DelayMessageHandlingException;
import com.frever.platform.timers.messaging.GroupDeletedMessage;
import com.frever.platform.timers.messaging.GroupUnfollowedMessage;
import com.frever.platform.timers.utils.AbstractAggregationService;
import com.frever.platform.timers.utils.entities.TimerExecution;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class FollowerStatsAggregationService extends AbstractAggregationService {
    public static final String TIMER_NAME = "follower-stats-aggregation";

    private static final String NEW_FOLLOWER_INFO = """
        select "FollowingId", "FollowerId", "IsMutual" from "Follower" where "Time" >= ? and "Time" < ?
        """;

    private static final String GROUP_DELETION_INFO = """
        select "Id" as id, "DeletedAt" is not null as deleted from "Group" where "Id" in (%s)
        """;

    private static final String FOLLOWER_STATS_BOOTSTRAP = """
        INSERT INTO stats.follower_stats (group_id, following_count, followers_count, friends_count, deleted)
        SELECT g."Id" AS group_id,
            (SELECT count(*)::integer AS count
                   FROM "Follower" f0
                     JOIN "Group" g0 ON f0."FollowingId" = g0."Id"
                  WHERE g."Id" = f0."FollowerId" AND NOT g0."IsBlocked" AND g0."DeletedAt" IS NULL) AS following_count,
            (SELECT count(*)::integer AS count
                   FROM "Follower" f1
                     JOIN "Group" g1 ON f1."FollowerId" = g1."Id"
                  WHERE g."Id" = f1."FollowingId" AND NOT g1."IsBlocked" AND g1."DeletedAt" IS NULL) AS followers_count,
            (SELECT count(*)::integer AS count
                   FROM "Follower" f2
                     JOIN "Group" g2 ON f2."FollowerId" = g2."Id"
                  WHERE g."Id" = f2."FollowingId" AND f2."IsMutual" AND NOT g2."IsBlocked" AND g2."DeletedAt" IS NULL) AS friends_count,
            g."DeletedAt" IS NOT NULL AS deleted
        FROM "Group" g;
        """;
    private static final int RUN_INTERVAL = 3;

    @Inject
    FollowEdgeAggregationService followEdgeListAggregationService;

    @Override
    protected String getTimerName() {
        return TIMER_NAME;
    }

    @Scheduled(every = RUN_INTERVAL + "m", delay = 30, delayUnit = TimeUnit.SECONDS)
    @Lock(value = Lock.Type.WRITE, time = 60, unit = TimeUnit.SECONDS)
    public void aggregateFollowerStats() {
        Log.info("Aggregating follower_stats in 'stats' schema");
        Instant now = Instant.now();
        TimerExecution timerExecution = entityManager.find(TimerExecution.class, TIMER_NAME);
        if (timerExecution == null) {
            Log.info(TIMER_NAME + " has not run yet, need to bootstrap first.");
            return;
        }
        Instant lastRun = timerExecution.getLastExecutionTime();
        Instant until = now.minusSeconds(DelaySeconds);
        until = until.truncatedTo(ChronoUnit.MICROS);
        if (until.isBefore(lastRun)) {
            Log.info("Not enough data to aggregate follower_stats.");
            return;
        }
        aggregateFollowerStats(until, lastRun);
        timerExecution.setLastExecutionTime(until);
        entityManager.merge(timerExecution);
        Log.info("Done aggregating follower_stats in 'stats' schema.");
    }

    private void aggregateFollowerStats(Instant until, Instant lastRun) {
        try (var mainConnection = mainDataSource.getConnection();
             var st = mainConnection.createStatement();
             var ps = mainConnection.prepareStatement(NEW_FOLLOWER_INFO)) {
            ps.setObject(1, OffsetDateTime.ofInstant(lastRun, ZoneOffset.UTC));
            ps.setObject(2, OffsetDateTime.ofInstant(until, ZoneOffset.UTC));
            List<Follower> followers = new ArrayList<>();
            try (var resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    long followingId = resultSet.getLong("FollowingId");
                    long followerId = resultSet.getLong("FollowerId");
                    boolean isMutual = resultSet.getBoolean("IsMutual");
                    Follower follower = new Follower(followingId, followerId, isMutual);
                    followers.add(follower);
                }
            }
            Log.info("Found " + followers.size() + " new followers, lastRun: " + lastRun + ", until: " + until);
            if (followers.isEmpty()) {
                return;
            }
            followEdgeListAggregationService.aggregateFollowEdgeList(followers);
            Map<Long, Long> followerCountMap = new HashMap<>();
            Map<Long, Long> followingCountMap = new HashMap<>();
            Map<Long, Long> friendsCountMap = new HashMap<>();
            Set<Long> groupIds = new HashSet<>();
            for (Follower follower : followers) {
                long followingId = follower.followingId();
                followerCountMap.put(
                    followingId,
                    followerCountMap.getOrDefault(followingId, 0L) + 1
                );
                long followerId = follower.followerId();
                followingCountMap.put(
                    followerId,
                    followingCountMap.getOrDefault(followerId, 0L) + 1
                );
                if (follower.isMutual()) {
                    friendsCountMap.put(
                        followerId,
                        friendsCountMap.getOrDefault(followerId, 0L) + 1
                    );
                    friendsCountMap.put(
                        followingId,
                        friendsCountMap.getOrDefault(followingId, 0L) + 1
                    );
                }
                groupIds.add(followingId);
                groupIds.add(followerId);
            }
            String inClause = String.join(",", groupIds.stream().map(String::valueOf).collect(Collectors.toSet()));
            var sql = GROUP_DELETION_INFO.formatted(inClause);
            Map<Long, Boolean> deletedMap = new HashMap<>();
            try (var resultSet = st.executeQuery(sql)) {
                while (resultSet.next()) {
                    long groupId = resultSet.getLong("id");
                    boolean deleted = resultSet.getBoolean("deleted");
                    deletedMap.put(groupId, deleted);
                }
            }
            for (Long groupId : groupIds) {
                var followerStats = entityManager.find(FollowerStats.class, groupId);
                if (followerStats == null) {
                    followerStats = new FollowerStats();
                    followerStats.setGroupId(groupId);
                    followerStats.setFollowingCount(followingCountMap.getOrDefault(groupId, 0L));
                    followerStats.setFollowersCount(followerCountMap.getOrDefault(groupId, 0L));
                    followerStats.setFriendsCount(friendsCountMap.getOrDefault(groupId, 0L));
                    followerStats.setDeleted(deletedMap.getOrDefault(groupId, false));
                    entityManager.persist(followerStats);
                } else {
                    followerStats.setFollowingCount(
                        followerStats.getFollowingCount() + followingCountMap.getOrDefault(groupId, 0L));
                    followerStats.setFollowersCount(
                        followerStats.getFollowersCount() + followerCountMap.getOrDefault(groupId, 0L));
                    followerStats.setFriendsCount(
                        followerStats.getFriendsCount() + friendsCountMap.getOrDefault(groupId, 0L));
                    followerStats.setDeleted(deletedMap.getOrDefault(groupId, false));
                    entityManager.merge(followerStats);
                }
            }
        } catch (SQLException e) {
            Log.error("Failed to aggregate new follower_stats.", e);
            throw new RuntimeException(e);
        }
    }

    @Lock(value = Lock.Type.WRITE, time = 1, unit = TimeUnit.MINUTES)
    public void bootstrap() {
        Log.info("Bootstrapping data in 'stats' schema for follower_stats.");
        recordTimerExecution();
        cleanup();
        bootstrapFollowerStats();
        Log.info("Done bootstrapping data in 'stats' schema for follower_stats.");
    }

    private void bootstrapFollowerStats() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            int i = statement.executeUpdate(FOLLOWER_STATS_BOOTSTRAP);
            Log.info("Bootstrapped " + i + " rows in follower_stats table in 'stats' schema.");
        } catch (SQLException e) {
            String message = "Failed to bootstrap follower_stats table in 'stats' schema.";
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private void cleanup() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate table stats.follower_stats");
        } catch (SQLException e) {
            String message = "Failed to cleanup follower_stats table in 'stats' schema.";
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    public void handleGroupDeletedMessage(GroupDeletedMessage groupDeletedMessage) {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            int count = statement.executeUpdate(
                "update stats.follower_stats set deleted = true where group_id = " + groupDeletedMessage.groupId());
            if (count == 0) {
                Log.warn("GroupDeletedMessage not found, groupId: " + groupDeletedMessage.groupId());
                return;
            }
            handleFollowingsAndFriends(groupDeletedMessage, statement);
            handleFollower(groupDeletedMessage, statement);
            followEdgeListAggregationService.handleGroupDeleteMessage(groupDeletedMessage.groupId());
        } catch (SQLException e) {
            String message = "Failed to handle GroupDeletedMessage for group_id: " + groupDeletedMessage.groupId();
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private static void handleFollower(GroupDeletedMessage groupDeletedMessage, Statement statement)
        throws SQLException {
        try (var resultSet = statement.executeQuery("""
            select "FollowerId" from "Follower" where "FollowingId" = %d
            """.formatted(groupDeletedMessage.groupId()))) {
            Set<Long> followerIds = new HashSet<>();
            while (resultSet.next()) {
                long followerId = resultSet.getLong("FollowerId");
                followerIds.add(followerId);
            }
            int count = 0;
            for (Long followerId : followerIds) {
                statement.addBatch("""
                    update stats.follower_stats set followers_count = followers_count - 1
                    where group_id = %d
                    """.formatted(followerId));
                count++;
                if (count % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void handleFollowingsAndFriends(GroupDeletedMessage groupDeletedMessage, Statement statement)
        throws SQLException {
        try (var resultSet = statement.executeQuery("""
            select "FollowingId", "IsMutual" from "Follower" where "FollowerId" = %d
            """.formatted(groupDeletedMessage.groupId()))) {
            Set<Follower> followers = new HashSet<>();
            while (resultSet.next()) {
                long followingId = resultSet.getLong("FollowingId");
                boolean isMutual = resultSet.getBoolean("IsMutual");
                followers.add(new Follower(followingId, groupDeletedMessage.groupId(), isMutual));
            }
            int count = 0;
            for (Follower follower : followers) {
                statement.addBatch("""
                    update stats.follower_stats set following_count = following_count - 1
                    where group_id = %d
                    """.formatted(follower.followerId()));
                count++;
                if (follower.isMutual()) {
                    statement.addBatch("""
                        update stats.follower_stats set friends_count = friends_count - 1
                        where group_id = %d
                        """.formatted(follower.followerId()));
                    count++;
                }
                if (count % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    public void handleGroupUnfollowedMessage(GroupUnfollowedMessage groupUnfollowedMessage) {
        var lastRun = entityManager.find(TimerExecution.class, TIMER_NAME).getLastExecutionTime();
        if (lastRun == null) {
            Log.warn("FollowerStatsAggregationService has not run yet, need to bootstrap first.");
            throw new DelayMessageHandlingException(60);
        }
        var unfollowedAt =
            Objects.requireNonNullElse(groupUnfollowedMessage.unfollowedTime(), groupUnfollowedMessage.time());
        if (lastRun.isBefore(unfollowedAt)) {
            Log.info("GroupUnfollowedMessage is newer than last run, no need to decrease any number." + unfollowedAt);
            return;
        }
        var followerId = groupUnfollowedMessage.followerId();
        var followingId = groupUnfollowedMessage.followingId();
        var isMutual = groupUnfollowedMessage.isMutual();
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 select "FollowingId" from "Follower" where "FollowerId" = %d and "FollowingId" = %d and "IsMutual" = %s
                 """.formatted(
                 followerId,
                 followingId,
                 isMutual
             ))) {
            if (resultSet.next()) {
                Log.warnf(
                    "GroupUnfollowedMessage still exists, followerId: %s, followingId: %s, IsMutual: %s",
                    followerId,
                    followingId,
                    isMutual
                );
                throw new DelayMessageHandlingException(30);
            }
            if (followerId >= followingId) {
                statement.execute("""
                    update stats.follower_stats set following_count = following_count - 1
                    where group_id = %d
                    """.formatted(followerId));
                statement.execute("""
                    update stats.follower_stats set followers_count = followers_count - 1
                    where group_id = %d
                    """.formatted(followingId));
                if (isMutual) {
                    statement.execute("""
                        update stats.follower_stats set friends_count = friends_count - 1
                        where group_id = %d
                        """.formatted(followerId));
                    statement.execute("""
                        update stats.follower_stats set friends_count = friends_count - 1
                        where group_id = %d
                        """.formatted(followingId));
                }
            } else {
                statement.execute("""
                    update stats.follower_stats set followers_count = followers_count - 1
                    where group_id = %d
                    """.formatted(followingId));
                statement.execute("""
                    update stats.follower_stats set following_count = following_count - 1
                    where group_id = %d
                    """.formatted(followerId));
                if (isMutual) {
                    statement.execute("""
                        update stats.follower_stats set friends_count = friends_count - 1
                        where group_id = %d
                        """.formatted(followingId));
                    statement.execute("""
                        update stats.follower_stats set friends_count = friends_count - 1
                        where group_id = %d
                        """.formatted(followerId));
                }
            }
            followEdgeListAggregationService.handleGroupUnfollowedMessage(groupUnfollowedMessage);
        } catch (SQLException e) {
            String message = "Failed to handle GroupUnfollowedMessage for followerId: " + followerId + ", followingId: "
                + followingId;
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}

