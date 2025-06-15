package com.frever.platform.timers.videoKpi;

import static java.time.ZoneOffset.UTC;

import com.frever.platform.timers.messaging.DelayMessageHandlingException;
import com.frever.platform.timers.messaging.VideoUnlikedMessage;
import com.frever.platform.timers.utils.AbstractAggregationService;
import com.frever.platform.timers.utils.entities.TimerExecution;
import com.frever.platform.timers.videoKpi.entities.VideoKpi;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Transactional
public class VideoKpiAggregationService extends AbstractAggregationService {
    public static final String TIMER_NAME = "video-kpi-aggregation";
    private static final OffsetDateTime APP_START_TIME = OffsetDateTime.now();

    private static final String DELETED_VIDEOS = """
        select v."Id" as video_id from "Video" v
            where v."IsDeleted" = true and v."ModifiedTime" >= ? and v."ModifiedTime" < ?;
        """;

    private static final String VIDEO_LIKES_BOOTSTRAP = """
        INSERT INTO stats.video_kpi (video_id, likes, views, comments, shares, remixes, battles_won, battles_lost, deleted)
        WITH rt AS (
                SELECT synctime.t
                  FROM rollup.synctime
                LIMIT 1
               ), likes AS (
                SELECT "Likes"."VideoId",
                   count(*) AS num
                  FROM "Likes"
                 WHERE "Likes"."Time" >= (( SELECT rt.t
                          FROM rt))
                 GROUP BY "Likes"."VideoId"
               ), views AS (
                SELECT "Views"."VideoId",
                   count(*) AS num
                  FROM "Views"
                 WHERE "Views"."Time" >= (( SELECT rt.t
                          FROM rt))
                 GROUP BY "Views"."VideoId"
               ), comments AS (
                SELECT "Comments"."VideoId",
                   count(*) AS num
                  FROM "Comments"
                 WHERE "Comments"."Time" >= (( SELECT rt.t
                          FROM rt))
                 GROUP BY "Comments"."VideoId"
               ), shares AS (
                SELECT "Shares"."VideoId",
                   count(*) AS num
                  FROM "Shares"
                 WHERE "Shares"."Time" >= (( SELECT rt.t
                          FROM rt))
                 GROUP BY "Shares"."VideoId"
               ), remixes AS (
                 SELECT "RemixedFromVideoId" as "VideoId", count(*) AS num
                    from "Video" where "RemixedFromVideoId" is not null and not "IsDeleted"
                    group by "RemixedFromVideoId"
               ), battles AS (
                SELECT gg."VideoId",
                   sum(gg.won) AS won,
                   sum(gg.lost) AS lost
                  FROM ( SELECT "FinishedBattles"."VideoIdLeft" AS "VideoId",
                           count(*) FILTER (WHERE "FinishedBattles"."VideoLeftVotes" > "FinishedBattles"."VideoRightVotes") AS won,
                           count(*) FILTER (WHERE "FinishedBattles"."VideoRightVotes" > "FinishedBattles"."VideoLeftVotes") AS lost
                          FROM "FinishedBattles"
                         WHERE "FinishedBattles"."EndTime" > (( SELECT rt.t
                                  FROM rt))
                         GROUP BY "FinishedBattles"."VideoIdLeft"
                       UNION ALL
                        SELECT "FinishedBattles"."VideoIdRight" AS "VideoId",
                           count(*) FILTER (WHERE "FinishedBattles"."VideoLeftVotes" < "FinishedBattles"."VideoRightVotes") AS won,
                           count(*) FILTER (WHERE "FinishedBattles"."VideoRightVotes" < "FinishedBattles"."VideoLeftVotes") AS lost
                          FROM "FinishedBattles"
                         WHERE "FinishedBattles"."EndTime" > (( SELECT rt.t
                                  FROM rt))
                         GROUP BY "FinishedBattles"."VideoIdRight") gg
                 GROUP BY gg."VideoId"
               ), currstat AS (
                SELECT video."Id" AS "VideoId",
                   COALESCE(likes.num, 0::bigint) AS "Likes",
                   COALESCE(views.num, 0::bigint) AS "Views",
                   COALESCE(comments.num, 0::bigint) AS "Comments",
                   COALESCE(shares.num, 0::bigint) AS "Shares",
                   COALESCE(remixes.num, 0::bigint) AS "Remixes",
                   COALESCE(battles.won, 0::numeric) AS "BattlesWon",
                   COALESCE(battles.lost, 0::numeric) AS "BattlesLost",
                   video."IsDeleted" AS "Deleted"
                  FROM "Video" video
                    FULL JOIN likes ON video."Id" = likes."VideoId"
                    FULL JOIN views ON video."Id" = views."VideoId"
                    FULL JOIN comments ON video."Id" = comments."VideoId"
                    FULL JOIN shares ON video."Id" = shares."VideoId"
                    FULL JOIN remixes ON video."Id" = remixes."VideoId"
                    FULL JOIN battles ON video."Id" = battles."VideoId"
               )
        SELECT COALESCE(currstat."VideoId", "VideoRollup"."VideoId") AS video_id,
            COALESCE(currstat."Likes", 0::bigint) + COALESCE("VideoRollup"."Likes", 0::bigint) AS likes,
            COALESCE(currstat."Views", 0::bigint) + COALESCE("VideoRollup"."Views", 0::bigint) AS views,
            COALESCE(currstat."Comments", 0::bigint) + COALESCE("VideoRollup"."Comments", 0::bigint) AS comments,
            COALESCE(currstat."Shares", 0::bigint) + COALESCE("VideoRollup"."Shares", 0::bigint) AS shares,
            COALESCE(currstat."Remixes", 0::bigint) AS remixes,
            COALESCE(currstat."BattlesWon", 0::numeric) + COALESCE("VideoRollup"."BattlesWon", 0::bigint)::numeric AS battles_won,
            COALESCE(currstat."BattlesLost", 0::numeric) + COALESCE("VideoRollup"."BattlesLost", 0::bigint)::numeric AS battles_lost,
            COALESCE(currstat."Deleted", false) AS deleted
        FROM currstat
            FULL JOIN rollup."VideoRollup" ON currstat."VideoId" = "VideoRollup"."VideoId";
        """;
    private static final String VIDEO_LIKES_INTERVAL = """
        select v."Id" as video_id,
            count(l."UserId") as likes
        from "Video" v
            inner join "Likes" l on v."Id" = l."VideoId"
        where l."Time" >= ? and l."Time" < ? and v."IsDeleted" = false
            group by v."Id" order by v."Id";
        """;
    private static final String VIDEO_RATING_INTERVAL = """
        select vr."VideoId" as video_id,
            sum(vr."Rating") as rating
        from "VideoRating" vr
            inner join "Video" v on vr."VideoId" = v."Id"
        where vr."Time" >= ? and vr."Time" < ? and v."IsDeleted" = false
            group by vr."VideoId"
        """;
    private static final String VIDEO_VIEWS_INTERVAL = """
        select v."Id" as video_id,
            count(vi."UserId") as views
        from "Video" v
            inner join "Views" vi on v."Id" = vi."VideoId"
        where vi.created >= ? and vi.created < ? and v."IsDeleted" = false
            group by v."Id" order by v."Id";
        """;
    private static final String VIDEO_COMMENTS_INTERVAL = """
        select v."Id" as video_id,
            count(c."GroupId") as comments
        from "Video" v
            inner join "Comments" c on v."Id" = c."VideoId"
        where c."Time" >= ? and c."Time" < ? and v."IsDeleted" = false
            group by v."Id" order by v."Id";
        """;
    private static final String VIDEO_REMIXES_INTERVAL = """
        select v."RemixedFromVideoId" as video_id,
            COALESCE(count(v.*), 0) as remixes
        from "Video" v
        where v."ModifiedTime" >= ? and v."ModifiedTime" < ?
        and v."Access" = 'Public' and v."PublishTypeId" != 2 and v."IsDeleted" = false
        and v."RemixedFromVideoId" is not null
        group by v."RemixedFromVideoId" order by v."RemixedFromVideoId";
        """;
    // "FinishedBattles" table is empty even in production
    private static final String VIDEO_BATTLES_INTERVAL = """
        select v."Id" as video_id,
            COALESCE(count(fb_left."VideoIdRight") FILTER (WHERE fb_left."VideoLeftVotes" > fb_left."VideoRightVotes")
                + count(fb_right."VideoIdLeft") FILTER (WHERE fb_right."VideoLeftVotes" < fb_right."VideoRightVotes") , 0) as battles_won,
            COALESCE(count(fb_left."VideoIdRight") FILTER (WHERE fb_left."VideoLeftVotes" < fb_left."VideoRightVotes")
                + count(fb_right."VideoIdLeft") FILTER (WHERE fb_right."VideoLeftVotes" > fb_right."VideoRightVotes") , 0) as battles_lost
        from "Video" v
            inner join "FinishedBattles" fb_left on v."Id" = fb_left."VideoIdLeft"
            inner join "FinishedBattles" fb_right on v."Id" = fb_right."VideoIdRight"
        where fb_left."EndTime" >= ? and fb_right."EndTime" >= ? and fb_left."EndTime" < ? and fb_right."EndTime" < ?
            and v."IsDeleted" = false
            group by v."Id" order by v."Id";
        """;
    private static final int RUN_INTERVAL = 3;

    @Scheduled(every = RUN_INTERVAL + "m", delay = 30, delayUnit = TimeUnit.SECONDS)
    @Lock(value = Lock.Type.WRITE, time = 60, unit = TimeUnit.SECONDS)
    public void aggregateVideoKpi() {
        Log.info("Aggregating video_kpi in 'stats' schema");
        Instant now = Instant.now();
        TimerExecution timerExecution = entityManager.find(TimerExecution.class, TIMER_NAME);
        if (timerExecution == null) {
            Log.info(TIMER_NAME + " has not run yet, need to bootstrap first.");
            return;
        }
        var lastRun = OffsetDateTime.ofInstant(timerExecution.getLastExecutionTime(), UTC);
        Instant untilInstant = now.minusSeconds(DelaySeconds);
        untilInstant = untilInstant.truncatedTo(ChronoUnit.MICROS);
        var until = OffsetDateTime.ofInstant(untilInstant, UTC);
        if (until.isBefore(lastRun)) {
            Log.info("Not enough data to aggregate video_kpi.");
            return;
        }
        aggregateVideoKpi(lastRun, until);
        timerExecution.setLastExecutionTime(untilInstant);
        entityManager.merge(timerExecution);
        Log.info("Done aggregating video_kpi in 'stats' schema.");
    }

    private void setVideoDeleted(OffsetDateTime lastRun, OffsetDateTime until) {
        try (var connection = mainDataSource.getConnection();
             var statement = connection.prepareStatement(DELETED_VIDEOS)) {
            statement.setObject(1, lastRun);
            statement.setObject(2, until);
            int deleted = 0;
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long videoId = resultSet.getLong("video_id");
                    VideoKpi videoKpi = entityManager.find(VideoKpi.class, videoId);
                    deleted++;
                    if (videoKpi != null) {
                        videoKpi.setDeleted(true);
                        entityManager.merge(videoKpi);
                    }
                }
            }
            Log.info("Set " + deleted + " videos as deleted in 'stats' schema.");
        } catch (SQLException e) {
            Log.error("Failed to set deleted videos in 'stats' schema.", e);
            throw new RuntimeException("Failed to set deleted videos in 'stats' schema.", e);
        }
    }

    private void aggregateVideoKpi(OffsetDateTime lastRun, OffsetDateTime until) {
        try (var connection = mainDataSource.getConnection();
             var likes = connection.prepareStatement(VIDEO_LIKES_INTERVAL);
             var rating = connection.prepareStatement(VIDEO_RATING_INTERVAL);
             var views = connection.prepareStatement(VIDEO_VIEWS_INTERVAL);
             var comments = connection.prepareStatement(VIDEO_COMMENTS_INTERVAL);
             var remixes = connection.prepareStatement(VIDEO_REMIXES_INTERVAL);
             var battles = connection.prepareStatement(VIDEO_BATTLES_INTERVAL)) {
            Log.info("Aggregating video_kpi in 'stats' schema from " + lastRun + " to " + until);
            Map<Long, Integer> newLikes = newLikes(lastRun, until, likes);
            Map<Long, Integer> newLikesCombined = newLikesCombined(lastRun, until, rating, newLikes);
            Map<Long, Integer> newViews = newViews(lastRun, until, views);
            Map<Long, Integer> newComments = newComments(lastRun, until, comments);
            Map<Long, Integer> newRemixes = newRemixes(lastRun, until, remixes);
            Map<Long, int[]> newBattles = newBattles(lastRun, until, battles);
            combineData(newLikesCombined, newViews, newComments, newRemixes, newBattles);
            setVideoDeleted(lastRun, until);
        } catch (SQLException e) {
            Log.error("Failed to aggregate video_kpi in 'stats' schema.", e);
            throw new RuntimeException("Failed to aggregate video_kpi in 'stats' schema.", e);
        }
    }

    private void combineData(
        Map<Long, Integer> newLikes,
        Map<Long, Integer> newViews,
        Map<Long, Integer> newComments,
        Map<Long, Integer> newRemixes,
        Map<Long, int[]> newBattles
    ) {
        Set<Long> videoIds = new HashSet<>();
        videoIds.addAll(newLikes.keySet());
        videoIds.addAll(newViews.keySet());
        videoIds.addAll(newComments.keySet());
        videoIds.addAll(newRemixes.keySet());
        videoIds.addAll(newBattles.keySet());
        Log.info("Found " + videoIds.size() + " videos to aggregate.");
        if (videoIds.isEmpty()) {
            return;
        }
        for (long videoId : videoIds) {
            int likes = newLikes.getOrDefault(videoId, 0);
            int views = newViews.getOrDefault(videoId, 0);
            int comments = newComments.getOrDefault(videoId, 0);
            int remixes = newRemixes.getOrDefault(videoId, 0);
            int[] battles = newBattles.getOrDefault(videoId, new int[]{0, 0});
            int battlesWon = battles[0];
            int battlesLost = battles[1];
            VideoKpi videoKpi = entityManager.find(VideoKpi.class, videoId);
            if (videoKpi == null) {
                videoKpi = new VideoKpi();
                videoKpi.setVideoId(videoId);
                videoKpi.setLikes(likes);
                videoKpi.setViews(views);
                videoKpi.setComments(comments);
                videoKpi.setRemixes(remixes);
                videoKpi.setBattlesWon(battlesWon);
                videoKpi.setBattlesLost(battlesLost);
                entityManager.persist(videoKpi);
            } else {
                videoKpi.setLikes(videoKpi.getLikes() + likes);
                videoKpi.setViews(videoKpi.getViews() + views);
                videoKpi.setComments(videoKpi.getComments() + comments);
                videoKpi.setRemixes(videoKpi.getRemixes() + remixes);
                videoKpi.setBattlesWon(videoKpi.getBattlesWon() + battlesWon);
                videoKpi.setBattlesLost(videoKpi.getBattlesLost() + battlesLost);
                entityManager.merge(videoKpi);
            }
        }
    }

    private static Map<Long, int[]> newBattles(OffsetDateTime lastRun, OffsetDateTime until, PreparedStatement battles)
        throws SQLException {
        battles.setObject(1, lastRun);
        battles.setObject(2, lastRun);
        battles.setObject(3, until);
        battles.setObject(4, until);
        Map<Long, int[]> battlesMap = new HashMap<>();
        try (var resultSet = battles.executeQuery()) {
            while (resultSet.next()) {
                long videoId = resultSet.getLong("video_id");
                int battlesWon = resultSet.getInt("battles_won");
                int battlesLost = resultSet.getInt("battles_lost");
                battlesMap.put(videoId, new int[]{battlesWon, battlesLost});
            }
        }
        return battlesMap;
    }

    private static Map<Long, Integer> newRemixes(
        OffsetDateTime lastRun,
        OffsetDateTime until,
        PreparedStatement remixes
    ) throws SQLException {
        remixes.setObject(1, lastRun);
        remixes.setObject(2, until);
        Map<Long, Integer> remixesMap = new HashMap<>();
        try (var resultSet = remixes.executeQuery()) {
            while (resultSet.next()) {
                long videoId = resultSet.getLong("video_id");
                int remixesCount = resultSet.getInt("remixes");
                remixesMap.put(videoId, remixesCount);
            }
        }
        return remixesMap;

    }

    private static Map<Long, Integer> newComments(
        OffsetDateTime lastRun,
        OffsetDateTime until,
        PreparedStatement comments
    ) throws SQLException {
        comments.setObject(1, lastRun);
        comments.setObject(2, until);
        Map<Long, Integer> newComments = new HashMap<>();
        try (var resultSet = comments.executeQuery()) {
            while (resultSet.next()) {
                long videoId = resultSet.getLong("video_id");
                int commentsCount = resultSet.getInt("comments");
                newComments.put(videoId, commentsCount);
            }
        }
        return newComments;
    }

    private static Map<Long, Integer> newViews(OffsetDateTime lastRun, OffsetDateTime until, PreparedStatement views)
        throws SQLException {
        views.setObject(1, lastRun);
        views.setObject(2, until);
        Map<Long, Integer> viewsMap = new HashMap<>();
        try (var resultSet = views.executeQuery()) {
            while (resultSet.next()) {
                long videoId = resultSet.getLong("video_id");
                int viewsCount = resultSet.getInt("views");
                viewsMap.put(videoId, viewsCount);
            }
        }
        return viewsMap;
    }

    private static Map<Long, Integer> newLikes(OffsetDateTime lastRun, OffsetDateTime until, PreparedStatement likes)
        throws SQLException {
        likes.setObject(1, lastRun);
        likes.setObject(2, until);
        Map<Long, Integer> likesMap = new HashMap<>();
        try (var resultSet = likes.executeQuery()) {
            while (resultSet.next()) {
                long videoId = resultSet.getLong("video_id");
                int likesCount = resultSet.getInt("likes");
                likesMap.put(videoId, likesCount);
            }
        }
        return likesMap;
    }

    private static Map<Long, Integer> newLikesCombined(
        OffsetDateTime lastRun,
        OffsetDateTime until,
        PreparedStatement rating,
        Map<Long, Integer> newLikes
    ) throws SQLException {
        rating.setObject(1, lastRun);
        rating.setObject(2, until);
        Map<Long, Integer> likesCombined = new HashMap<>(newLikes);
        try (var resultSet = rating.executeQuery()) {
            while (resultSet.next()) {
                long videoId = resultSet.getLong("video_id");
                int ratingCount = resultSet.getInt("rating");
                likesCombined.compute(videoId, (_, v) -> v == null ? ratingCount : v + ratingCount);
            }
        }
        return likesCombined;
    }

    @Lock(value = Lock.Type.WRITE, time = 3, unit = TimeUnit.MINUTES)
    public void bootstrap() {
        Log.info("Bootstrapping data in 'stats' schema for video_kpi.");
        recordTimerExecution();
        cleanup();
        bootstrapVideoKpi();
        Log.info("Done bootstrapping data in 'stats' schema for video_kpi.");
    }

    private void bootstrapVideoKpi() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            int i = statement.executeUpdate(VIDEO_LIKES_BOOTSTRAP);
            Log.info("Bootstrapped " + i + " rows in follower_stats table in 'stats' schema.");
        } catch (SQLException e) {
            Log.error("Failed to bootstrap video_kpi aggregation table in 'stats' schema.", e);
        }
    }

    private void cleanup() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate table stats.video_kpi");
        } catch (SQLException e) {
            String message = "Failed to cleanup video_kpi aggregation table in 'stats' schema.";
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    @Override
    protected String getTimerName() {
        return TIMER_NAME;
    }

    public void handleVideoUnlikedMessage(VideoUnlikedMessage videoUnlikedMessage) {
        var lastRun = entityManager.find(TimerExecution.class, TIMER_NAME).getLastExecutionTime();
        if (lastRun == null) {
            Log.warn("VideoKpiAggregationService has not run yet, need to bootstrap first.");
            throw new DelayMessageHandlingException(60);
        }
        var unlikedAt = videoUnlikedMessage.time();
        if (unlikedAt != null && lastRun.isBefore(unlikedAt)) {
            Log.info("VideoUnlikedMessage is newer than last run, no need to decrease any number." + unlikedAt);
            return;
        }
        try {
            VideoKpi videoKpi = entityManager.find(VideoKpi.class, videoUnlikedMessage.videoId());
            if (videoKpi != null && videoKpi.getLikes() >= 1) {
                videoKpi.setLikes(videoKpi.getLikes() - 1);
                entityManager.merge(videoKpi);
            }
        } catch (Exception e) {
            Log.error("Failed to handle VideoUnlikedMessage.", e);
            throw new RuntimeException("Failed to handle VideoUnlikedMessage.", e);
        }
    }

    public void recalculateVideoKpiRemix() {
        Log.info("Recalculating video_kpi remixes in 'stats' schema.");
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            int cleared = statement.executeUpdate("update stats.video_kpi set remixes = 0;");
            Log.info("Cleared remixes in " + cleared + " rows in video_kpi table in 'stats' schema.");
            int i = statement.executeUpdate("""
                with remixes as (
                    select v."RemixedFromVideoId" as video_id, count(v.*) as num
                    from "Video" v
                    where v."RemixedFromVideoId" is not null
                        and not v."IsDeleted" and v."Access" = 'Public' and v."PublishTypeId" != 2
                    group by "RemixedFromVideoId"
                )
                update stats.video_kpi vk
                set remixes = r.num
                from remixes r
                where vk.video_id = r.video_id and remixes != r.num;
                """);
            Log.info("Recalculated " + i + " rows in video_kpi table in 'stats' schema.");
        } catch (SQLException e) {
            Log.error("Failed to recalculate video_kpi remixes in 'stats' schema.", e);
            throw new RuntimeException("Failed to recalculate video_kpi remixes in 'stats' schema.", e);
        }
    }

    public void initVideoLikesCombined() {
        Log.info("Initializing video_kpi combined likes in 'stats' schema.");
        var initLikesCombined = """
            with rating as (
                select vr."VideoId" as video_id, sum(vr."Rating") as rating
                from "VideoRating" vr
                where vr."Time" < ?
                group by vr."VideoId"
            ),
            likes_combined as (
                select vk.likes + coalesce(rating.rating, 0) as likes_combined, vk.video_id
                from stats.video_kpi vk
                inner join rating on vk.video_id = rating.video_id
            )
            update stats.video_kpi vk
                set likes = lc.likes_combined
            from likes_combined lc
                where vk.video_id = lc.video_id and vk.likes != lc.likes_combined;
            """;
        try (var connection = mainDataSource.getConnection();
             var statement = connection.prepareStatement(initLikesCombined)) {
            statement.setObject(1, APP_START_TIME);
            int i = statement.executeUpdate();
            Log.info("Initialized " + i + " rows in video_kpi table in 'stats' schema.");
        } catch (SQLException e) {
            Log.error("Failed to initialize video_kpi combined like in 'stats' schema.", e);
            throw new RuntimeException("Failed to initialize video_kpi combined like in 'stats' schema.", e);
        }
    }
}
