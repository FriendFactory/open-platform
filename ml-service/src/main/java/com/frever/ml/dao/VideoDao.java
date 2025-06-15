package com.frever.ml.dao;

import static com.frever.ml.utils.Constants.EXCLUDED_BODY_ANIMATION_ID;
import static com.frever.ml.utils.Constants.NUM_FEED_PERSONALIZED;
import static com.frever.ml.utils.Constants.NUM_TEMPLATE_RECOMMENDATIONS;
import static com.frever.ml.utils.Constants.TEMPLATE_ID_TO_EXCLUDE;
import static com.frever.ml.utils.Utils.fromVideoInfoList;

import com.frever.ml.dto.CandidateVideo;
import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.GeoLocation;
import com.frever.ml.dto.LikedAccount;
import com.frever.ml.dto.RecommendedVideo;
import com.frever.ml.dto.UserInfo;
import com.frever.ml.dto.VideoIdAndDistance;
import com.frever.ml.dto.VideoInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.statement.Query;

@ApplicationScoped
public class VideoDao extends BaseDao {
    private static final String LIKE_ACCOUNT = """
        with like_count as (select u."MainGroupId" as groupId,
                                v."GroupId"     as likedGroupId,
                                count(1)        as likeCount
                            from "Likes" l
                            inner join "User" u on l."UserId" = u."Id"
                            inner join "Video" v on l."VideoId" = v."Id"
                            where l."Time" > :recent and u."MainGroupId" = :groupId
                            group by u."MainGroupId", v."GroupId")
        select *,
            like_count.LikeCount::float8 / (select max(like_count.LikeCount::float8) from like_count) as ratio
        from like_count
        order by 3 desc
        limit :limit
        """;

    private static final String CANDIDATE_VIDEOS = """
        WITH viewed_videos AS (
            SELECT "Views"."VideoId" AS "VideoId"
                FROM "Views"
            WHERE "Views"."UserId" = :userId
                AND "Views"."Time" >= :lookBackDays
            GROUP BY "Views"."VideoId"
        ), liked_videos AS (
            SELECT "Likes"."VideoId" AS "VideoId"
                FROM "Likes"
            WHERE "Likes"."UserId" = :userId
                AND "Likes"."Time" >= :lookBackDays
            GROUP BY "Likes"."VideoId"
        ), level_ids AS (
            SELECT unnest(:excludedLevelIds) AS "LevelId"               -- ARRAY[level_ids_to_exclude]
        ), blocked_user_ids AS (
            SELECT unnest(:blockedUsers) AS "BlockedUserId"             -- ARRAY[blocked_users]
        )
        SELECT
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
        FROM "Video" v
        WHERE v."GroupId" != :groupId             -- Not my videos
            AND v."IsDeleted" = false             -- Not deleted
            AND v."RemixedFromVideoId" IS NULL    -- No remixes
            AND v."Access" = 'Public'             -- Public videos
            %s                                    -- Cold start or not (AND "Video"."StartListItem" IS NULL)
            AND v."SchoolTaskId" IS NULL          -- No school task
            AND v."PublishTypeId" != 2
            AND NOT (
                EXISTS (                           -- The video is not reported
                    SELECT 1 FROM "VideoReport"
                    WHERE "VideoReport"."VideoId" = v."Id" AND "VideoReport"."HideVideo" = true
                        and "VideoReport"."ClosedTime" is null
                )
            )
            AND v."CreatedTime" >= :lookBackDays
            AND (NOT (v."GroupId" = ANY (:excludedGroupIds)))
            AND NOT (
                EXISTS (
                    SELECT 1 FROM viewed_videos
                    WHERE viewed_videos."VideoId" = v."Id"
                )
            )
            AND NOT (
                EXISTS (
                    SELECT 1 FROM liked_videos
                    WHERE liked_videos."VideoId" = v."Id"
                )
            )
            %s                                          -- AND "Video"."Country" IN (?)
            %s                                          -- AND "Video"."Language" IN (?)
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
        ORDER BY v."CreatedTime" DESC
        LIMIT :limit
        """;

    private static final String FOLLOWING_VIDEOS = """
        WITH friends as (
            select "FollowingId" as friendId from "Follower" f
                inner join "Group" g on f."FollowingId" = g."Id"
                where f."FollowerId" = :groupId and not g."IsBlocked" AND g."DeletedAt" IS NULL
        ), viewed_videos AS (
            SELECT "Views"."VideoId" AS "VideoId"
                FROM "Views"
            WHERE "Views"."UserId" = :userId
                AND "Views"."Time" >= :lookBackDays
            GROUP BY "Views"."VideoId"
        ), liked_videos AS (
            SELECT "Likes"."VideoId" AS "VideoId"
                FROM "Likes"
            WHERE "Likes"."UserId" = :userId
                AND "Likes"."Time" >= :lookBackDays
            GROUP BY "Likes"."VideoId"
        ), level_ids AS (
            SELECT unnest(:excludedLevelIds) AS "LevelId"               -- ARRAY[level_ids_to_exclude]
        ), blocked_user_ids AS (
            SELECT unnest(:blockedUsers) AS "BlockedUserId"             -- ARRAY[blocked_users]
        )
        SELECT
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
        FROM "Video" v
        inner join friends f on v."GroupId" = f.friendId
            AND v."IsDeleted" = false             -- Not deleted
            AND v."RemixedFromVideoId" IS NULL    -- No remixes
            AND v."Access" = 'Public'             -- Public videos
            AND v."StartListItem" IS NULL         -- Not cold start
            AND v."SchoolTaskId" IS NULL          -- No school task
            AND v."PublishTypeId" != 2
            AND (NOT (v."GroupId" = ANY (:excludedGroupIds)))
            AND NOT (
                EXISTS (                           -- The video is not reported
                    SELECT 1 FROM "VideoReport"
                    WHERE "VideoReport"."VideoId" = v."Id" AND "VideoReport"."HideVideo" = true
                        and "VideoReport"."ClosedTime" is null
                )
            )
            AND v."CreatedTime" >= :lookBackDays
            AND NOT (
                EXISTS (
                    SELECT 1 FROM viewed_videos
                    WHERE viewed_videos."VideoId" = v."Id"
                )
            )
            AND NOT (
                EXISTS (
                    SELECT 1 FROM liked_videos
                    WHERE liked_videos."VideoId" = v."Id"
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
        ORDER BY v."CreatedTime" DESC
        LIMIT :limit
        """;

    private static final String VIDEO_DISTANCE = """
        select "Id" as videoId, ST_Distance("Location", ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)) as distance
        FROM "Video"
        WHERE "Id" in (<videoIds>) and "Location" is not null
        """;

    private static final String GET_TEMPLATE_IDS_FROM_VIDEO_IDS = """
        SELECT "TemplateIds" as template_ids FROM "Video" WHERE "Id" in (<videoIds>)
        """;

    @Inject
    UserDao userDao;

    public List<LikedAccount> getLikedAccounts(long groupId, int lookBackDays) {
        var recent = Instant.now().minus(lookBackDays, ChronoUnit.DAYS);
        return jdbi.withHandle(handle -> handle.createQuery(LIKE_ACCOUNT)
            .bind("recent", recent)
            .bind("groupId", groupId)
            .bind("limit", 500)
            .mapTo(LikedAccount.class)
            .list());
    }

    public List<RecommendedVideo> getColdStartRecommendations(
        UserInfo userInfo,
        List<Long> schoolTaskIds,
        GeoCluster geoCluster,
        List<Long> blockedUsers
    ) {
        long[] excludedGroupIds = userDao.getExcludedGroupIds().stream().mapToLong(Long::longValue).toArray();
        var videoInfo = getCandidateVideos(
            userInfo.groupId(),
            userInfo.userId(),
            schoolTaskIds,
            blockedUsers,
            excludedGroupIds,
            geoCluster,
            true
        );
        if (videoInfo.isEmpty()) {
            return Collections.emptyList();
        }
        return fromVideoInfoList(videoInfo, geoCluster, 1, "ColdStart");
    }

    public List<VideoInfo> getCandidateVideos(
        long groupId, long userId, List<Long> levelIdsToExclude, List<Long> blockedUsers, long[] groupIdsToExclude,
        GeoCluster geoCluster, boolean coldStart
    ) {
        int lookBackDays = geoCluster.numberOfDays();
        long limit = geoCluster.numberOfVideos();
        var coldStartClause =
            coldStart ? "AND v.\"StartListItem\" IS NOT NULL" : "AND v.\"StartListItem\" IS NULL ";
        var tempCountryClause = "";
        if (!geoCluster.allCountryAllowed()) {
            tempCountryClause = "AND v.\"Country\" = ANY (:countryCodes)";
        }
        var countryClause = tempCountryClause;
        var tempLanguageClause = "";
        if (!geoCluster.allLanguageAllowed()) {
            tempLanguageClause = "AND v.\"Language\" = ANY (:languageCodes)";
        }
        var languageClause = tempLanguageClause;
        return jdbi.withHandle(handle -> {
            Query bind =
                handle.createQuery(CANDIDATE_VIDEOS.formatted(coldStartClause, countryClause, languageClause))
                    .bind("groupId", groupId)
                    .bind("userId", userId)
                    .bind("lookBackDays", Instant.now().minus(lookBackDays, ChronoUnit.DAYS))
                    .bind("excludedGroupIds", groupIdsToExclude)
                    .bind("excludedBodyAnimations", EXCLUDED_BODY_ANIMATION_ID)
                    .bindArray("excludedLevelIds", Long.class, levelIdsToExclude)
                    .bindArray("blockedUsers", Long.class, blockedUsers)
                    .bind("blockedTemplate", TEMPLATE_ID_TO_EXCLUDE)
                    .bind("limit", limit);
            if (!countryClause.isEmpty()) {
                bind.bindArray("countryCodes", String.class, geoCluster.includeVideoFromCountry());
            }
            if (!languageClause.isEmpty()) {
                bind.bindArray("languageCodes", String.class, geoCluster.includeVideoWithLanguage());
            }
            return bind.mapTo(VideoInfo.class).list();
        });
    }

    public List<VideoIdAndDistance> getVideoDistanceInfo(List<Long> videoIds, GeoLocation geoLocation) {
        if (videoIds.isEmpty()) {
            return Collections.emptyList();
        }
        return jdbi.withHandle(handle -> {
            Query bind = handle.createQuery(VIDEO_DISTANCE)
                .bind("lon", geoLocation.longitude())
                .bind("lat", geoLocation.latitude())
                .bindList("videoIds", videoIds);
            return bind.mapTo(VideoIdAndDistance.class).list();
        });
    }

    public List<CandidateVideo> getNonColdStartVideos(
        UserInfo userInfo,
        List<Long> schoolTaskIds,
        List<GeoCluster> geoClusters,
        List<Long> blockedUsers
    ) {
        var result = new ArrayList<CandidateVideo>();
        var count = 0;
        long[] excludedGroupIds = userDao.getExcludedGroupIds().stream().mapToLong(Long::longValue).toArray();
        for (var geoCluster : geoClusters) {
            var videoInfo = getCandidateVideos(
                userInfo.groupId(),
                userInfo.userId(),
                schoolTaskIds,
                blockedUsers,
                excludedGroupIds,
                geoCluster,
                false
            );
            if (!videoInfo.isEmpty()) {
                result.addAll(videoInfo.stream()
                    .map(v -> new CandidateVideo(v, geoCluster.priority()))
                    .toList());
                count += videoInfo.size();
            }
            if (count >= geoCluster.numberOfVideos()) {
                return result;
            }
        }
        return result;
    }

    public List<VideoInfo> getFollowingVideos(
        UserInfo userInfo,
        List<Long> levelIdsToExclude,
        List<Long> blockedUsers
    ) {
        int lookBackDays = 10;
        long[] excludedGroupIds = userDao.getExcludedGroupIds().stream().mapToLong(Long::longValue).toArray();
        return jdbi.withHandle(handle -> {
            Query bind =
                handle.createQuery(FOLLOWING_VIDEOS)
                    .bind("groupId", userInfo.groupId())
                    .bind("userId", userInfo.userId())
                    .bind("lookBackDays", Instant.now().minus(lookBackDays, ChronoUnit.DAYS))
                    .bind("excludedGroupIds", excludedGroupIds)
                    .bind("excludedBodyAnimations", EXCLUDED_BODY_ANIMATION_ID)
                    .bindArray("excludedLevelIds", Long.class, levelIdsToExclude)
                    .bindArray("blockedUsers", Long.class, blockedUsers)
                    .bind("blockedTemplate", TEMPLATE_ID_TO_EXCLUDE)
                    .bind("limit", NUM_FEED_PERSONALIZED);
            return bind.mapTo(VideoInfo.class).list();
        });
    }

    public List<Long> getTemplateIdsFromVideoIds(List<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Collections.emptyList();
        }
        return jdbi.withHandle(handle -> {
            Query bind = handle.createQuery(GET_TEMPLATE_IDS_FROM_VIDEO_IDS)
                .bindList("videoIds", videoIds);
            List<Long> allTemplateIds = bind.mapTo(new GenericType<List<Long>>() {
            }).stream().flatMap(List::stream).toList();
            List<Long> result = new ArrayList<>(NUM_TEMPLATE_RECOMMENDATIONS);
            Set<Long> existing = new HashSet<>(NUM_TEMPLATE_RECOMMENDATIONS);
            for (var templateId : allTemplateIds) {
                if (result.size() >= NUM_TEMPLATE_RECOMMENDATIONS) {
                    break;
                }
                if (existing.add(templateId)) {
                    result.add(templateId);
                }
            }

            return result;
        });
    }
}
