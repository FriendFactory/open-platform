package com.frever.ml.dao;

import static com.frever.ml.utils.Constants.EXCLUDED_BODY_ANIMATION_ID;
import static com.frever.ml.utils.Constants.NUM_FEED_TEMPLATE_RANKING;
import static com.frever.ml.utils.Constants.TEMPLATE_ID_TO_EXCLUDE;
import static com.frever.ml.utils.Utils.fromVideoInfoList;

import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.RecommendedVideo;
import com.frever.ml.dto.UserInfo;
import com.frever.ml.dto.VideoInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class TemplateDao extends BaseDao {
    private static final String CURATED_TEMPLATES = """
        SELECT "Id" FROM "Template" WHERE not "IsDeleted" and "ReadinessId" = 2 and "TrendingSortingOrder" is not null
            ORDER BY "TrendingSortingOrder"
        """;

    private static final String VIDEOS_BASED_ON_TEMPLATE_RANKING = """
        WITH level_ids AS (
            SELECT unnest(:excludedLevelIds) AS "LevelId"               -- ARRAY[level_ids_to_exclude]
        ), blocked_user_ids AS (
            SELECT unnest(:blockedUsers) AS "BlockedUserId"             -- ARRAY[blocked_users]
        )
        select
            v."Id" as videoId,
            v."GroupId",
            v."CreatedTime",
            v."ExternalSongIds",
            v."SongInfo",
            v."UserSoundInfo",
            v."StartListItem",
            v."Country",
            v."Language",
            v."GeneratedTemplateId"
        from "Video" v
        inner join stats.template_data td on td.original_video_id = v."Id" and td.is_deleted = false and td.readiness_id = 2
        inner join stats.template_ranking tr on td.id = tr.template_id
        WHERE v."GroupId" != :groupId             -- Not my videos
            AND v."IsDeleted" = false             -- Not deleted
            AND v."RemixedFromVideoId" IS NULL    -- No remixes
            AND v."Access" = 'Public'             -- Public videos
            AND v."StartListItem" IS NULL         -- No cold start
            AND v."SchoolTaskId" IS NULL          -- No school task
            AND v."PublishTypeId" != 2
            AND NOT (
                EXISTS (                           -- The video is not reported
                    SELECT 1 FROM "VideoReport"
                    WHERE "VideoReport"."VideoId" = v."Id" AND "VideoReport"."HideVideo" = true
                        and "VideoReport"."ClosedTime" is null
                )
            )
            AND (NOT (v."GroupId" = ANY (:excludedGroupIds)))
            AND NOT (
                EXISTS (
                    SELECT 1 FROM "Views" viewed_videos
                    WHERE viewed_videos."VideoId" = v."Id" and viewed_videos."UserId" = :userId
                )
            )
            AND NOT (
                EXISTS (
                    SELECT 1 FROM "Likes" liked_videos
                    WHERE liked_videos."VideoId" = v."Id" and liked_videos."UserId" = :userId
                )
            )
            AND NOT (
                EXISTS (
                    SELECT level_ids."LevelId"
                    FROM level_ids
                    WHERE v."LevelId" = level_ids."LevelId"
                )
            )
            AND NOT (
                EXISTS (
                    SELECT blocked_user_ids."BlockedUserId"
                    FROM blocked_user_ids
                    WHERE v."GroupId" = blocked_user_ids."BlockedUserId"
                )
            )
            AND NOT (
                EXISTS (
                    SELECT 1
                    FROM "Video" v2
                    WHERE v2."GroupId" = :groupId and v2."TemplateIds" @> array[tr.template_id]
                )
            )
            AND NOT (v."TemplateIds" @> array[:blockedTemplate])
            AND NOT EXISTS (
                select 1 from "Level" level
                    inner join "Event" event on level."Id" = event."LevelId"
                    inner join "CharacterController" cc on cc."EventId" = event."Id"
                    inner join "CharacterControllerBodyAnimation" ccb on ccb."CharacterControllerId" = cc."Id"
                where (ccb."PrimaryBodyAnimationId" = ANY (:excludedBodyAnimations))
                    -- OR ((ccb."LowerBodyAnimationId" = ANY (:excludedBodyAnimations)) and ccb."LowerBodyAnimationId" is not null))
                    and level."Id" = v."LevelId" and v."LevelId" is not null
            )
        ORDER BY tr.rank
        LIMIT :limit
        """;

    @Inject
    UserDao userDao;

    public List<Long> getCuratedTemplates() {
        return jdbi.withHandle(handle -> handle.createQuery(CURATED_TEMPLATES).mapTo(Long.class).list());
    }

    public List<VideoInfo> getVideoInfoBasedOnTemplateRanking(
        long groupId,
        long userId,
        List<Long> levelIdsToExclude,
        List<Long> blockedUsers,
        long[] groupIdsToExclude,
        GeoCluster geoCluster
    ) {
        int lookBackDays = geoCluster.numberOfDays();
        return jdbi.withHandle(handle -> handle.createQuery(VIDEOS_BASED_ON_TEMPLATE_RANKING)
            .bind("userId", userId)
            .bind("lookBackDays", Instant.now().minus(lookBackDays, ChronoUnit.DAYS))
            .bind("excludedGroupIds", groupIdsToExclude)
            .bind("excludedBodyAnimations", EXCLUDED_BODY_ANIMATION_ID)
            .bindArray("excludedLevelIds", Long.class, levelIdsToExclude)
            .bindArray("blockedUsers", Long.class, blockedUsers)
            .bind("groupId", groupId)
            .bind("blockedTemplate", TEMPLATE_ID_TO_EXCLUDE)
            .bind("limit", NUM_FEED_TEMPLATE_RANKING)
            .mapTo(VideoInfo.class)
            .list());
    }

    public List<RecommendedVideo> getVideosBasedOnTemplateRanking(
        UserInfo userInfo, List<Long> levelIdsToExclude, GeoCluster geoCluster, List<Long> blockedUsers, int orderStart
    ) {
        long[] excludedGroupIds = userDao.getExcludedGroupIds().stream().mapToLong(Long::longValue).toArray();
        var videoInfo = getVideoInfoBasedOnTemplateRanking(
            userInfo.groupId(),
            userInfo.userId(),
            levelIdsToExclude,
            blockedUsers,
            excludedGroupIds,
            geoCluster
        );
        return fromVideoInfoList(videoInfo, geoCluster, orderStart, "TemplateRank");
    }
}
