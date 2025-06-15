package com.frever.platform.timers.template;

import static java.util.Objects.requireNonNullElse;

import com.frever.platform.timers.messaging.GroupChangedMessage;
import com.frever.platform.timers.messaging.TemplateUpdatedMessage;
import com.frever.platform.timers.messaging.VideoTemplateMappingMessage;
import com.frever.platform.timers.template.entities.Template;
import com.frever.platform.timers.template.entities.TemplateData;
import com.frever.platform.timers.template.entities.VideoIdAndTemplateId;
import com.frever.platform.timers.template.entities.VideoTemplateMap;
import com.frever.platform.timers.utils.AbstractAggregationService;
import com.frever.platform.timers.utils.CharacterInfo;
import com.frever.platform.timers.utils.CountryAndLanguageAndNickName;
import com.frever.platform.timers.utils.entities.TimerExecution;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityExistsException;
import jakarta.transaction.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class TemplateAggregationService extends AbstractAggregationService {
    public static final String TIMER_NAME = "template-aggregation";
    private static final int FETCH_SIZE = 10000;
    private static final int FETCH_RETRIES = 15;
    private static final long RETRY_INTERVAL = 200;

    private static final String ALL_TEMPLATES = """
        select t from Template t
        """;

    // https://github.com/FriendFactory/Server/blob/development/Microservices/Video/Frever.Video.Shared/GeoClusters/GeoClusterProvider.cs#L18-L19
    private static final String INFO_FROM_GROUP_TABLE = """
        select g."Id" as id, g."NickName" as nickName, COALESCE(c."ISOName", 'swe') as country, COALESCE(l."IsoCode", 'swe') as language
            from "Group" g left join "Country" c on g."TaxationCountryId" = c."Id"
            left join "Language" l on g."DefaultLanguageId" = l."Id"
            where g."Id" in (IN_CLAUSE);
        """;

    private static final String INFO_FROM_USER_TABLE = """
        select u."MainGroupId" as groupId, COALESCE(u."MainCharacterId", -1) as characterId, COALESCE(c."FilesInfo", '{}') as files
            from "User" u left join "Character" c on u."MainCharacterId" = c."Id"
            where u."MainGroupId" in (IN_CLAUSE);
        """;

    private static final String EXTERNAL_SONG_IDS_FROM_MUSIC_CONTROLLER = """
        select e."Id" as event_id, COALESCE(array_agg(distinct m."ExternalTrackId") filter (where m."ExternalTrackId" is not null), '{}') as external_track_ids
            from "MusicController" m inner join "Event" e on m."EventId" = e."Id"
            where e."Id" in (IN_CLAUSE) and m."ExternalTrackId" is not null
            group by e."Id";
        """;

    private static final String SONG_IDS_FROM_MUSIC_CONTROLLER = """
        select e."Id" as event_id, COALESCE(array_agg(distinct m."SongId") filter (where m."SongId" is not null), '{}') as song_ids
            from "MusicController" m inner join "Event" e on m."EventId" = e."Id"
            where e."Id" in (IN_CLAUSE) and m."SongId" is not null
            group by e."Id";
        """;

    private static final String INFO_FROM_EVENT = """
        select e."Id" as id, e."LevelId" as levelId
            from "Event" e where e."Id" in (IN_CLAUSE);
        """;

    private static final String VIDEO_ID_FROM_LEVEL_ID = """
        select v."Id" as video_id, v."LevelId" as level_id from "Video" v where v."LevelId" in (IN_CLAUSE);
        """;

    private static final String INFO_FROM_TEMPLATE_SUB_CATEGORY = """
        select t."Id" as id, t."TemplateCategoryId" as templateCategoryId
            from "TemplateSubCategory" AS t WHERE t."Id" in (IN_CLAUSE);
        """;

    private static final String INFO_FROM_VIDEO = """
        select v."Id" as id, v."TemplateIds" as templateIds, v."ModifiedTime" as modifiedTime
            from "Video" v WHERE v."TemplateIds" != '{}';
        """;

    private static final String TEMPLATE_USAGE_COUNT_BY_TEMPLATE_IDS = """
        select vt.template_id, count(vt.video_id) as count
            from stats.video_template_map vt where vt.template_id in (IN_CLAUSE)
            group by vt.template_id;
        """;

    private static final String MODIFIED_USERS = """
        select u."MainGroupId" as groupId, COALESCE(u."MainCharacterId", -1) as characterId, COALESCE(c."FilesInfo", '{}') as files
            from "User" u inner join "Character" c on u."MainCharacterId" = c."Id"
            where u."ModifiedTime" > ?;
        """;

    private static final String MODIFIED_GROUPS = """
        select g."Id" as id, g."NickName" as nickName, c."ISOName" as country, l."IsoCode" as language
            from "Group" g inner join "Country" c on g."TaxationCountryId" = c."Id"
            inner join "Language" l on g."DefaultLanguageId" = l."Id"
            where g."ModifiedTime" > ?;
        """;

    private static final String MODIFIED_CHARACTERS = """
        select u."MainGroupId" as groupId, c."FilesInfo" as files
            from "Character" c inner join "User" u on u."MainCharacterId" = c."Id"
            where c."ModifiedTime" > ?;
        """;

    private static final String MODIFIED_MUSIC_CONTROLLERS = """
        select e."Id" as event_id, COALESCE(array_agg(m."ExternalTrackId") filter (where m."ExternalTrackId" is not null), '{}') as external_track_ids
            from "MusicController" m inner join "Event" e on m."EventId" = e."Id"
            where e."ModifiedTime" > ? and m."ExternalTrackId" is not null
            group by e."Id";
        """;

    private static final String MODIFIED_VIDEOS = """
        select v."Id" as id, v."TemplateIds" as templateIds, v."ModifiedTime" as modifiedTime
            from "Video" v WHERE v."ModifiedTime" > ? and v."TemplateIds" != '{}' and v."TemplateIds" is not null;
        """;

    private static final String CHECK_VIDEO_TEMPLATE_MAP_EXISTENCE = """
        select video_id, template_id from stats.video_template_map where (video_id, template_id) in (IN_CLAUSE);
        """;

    private static final String TEMPLATE_DATA_BY_TEMPLATE_IDS = """
        select td from TemplateData td where td.id in (:ids)
        """;

    private static final String TEMPLATE_BY_IDS = """
        select t from Template t where t.id in (:ids)
        """;

    @Scheduled(every = "5m", delay = 30, delayUnit = TimeUnit.SECONDS)
    @Lock(value = Lock.Type.WRITE, time = 60, unit = TimeUnit.SECONDS)
    public void aggregateTemplateData() {
        Log.info("Aggregating template_data in 'stats' schema");
        Instant now = Instant.now();
        TimerExecution timerExecution = entityManager.find(TimerExecution.class, TIMER_NAME);
        if (timerExecution == null) {
            Log.info(TIMER_NAME + " has not run yet, need to bootstrap first.");
            return;
        }
        Instant lastRun = timerExecution.getLastExecutionTime();
        // finding modified data through modifiedTime column is not good enough, so many changed to use SNS&SQS
        // check "User" table for updated "MainCharacterId"
        // aggregateModifiedUsers(lastRun);
        // check "Character" table for updated "FilesInfo"
        // aggregateModifiedCharacters(lastRun);
        // check "Group" table for updated "TaxationCountryId" and "DefaultLanguageId"
        // aggregateModifiedGroups(lastRun);
        // check "MusicController" table for updated "ExternalTrackId", by joining "Event" table
        aggregateModifiedMusicControllers(lastRun);
        // check "Video" table in "Video" DB, for "TemplateIds" -- Most probably TemplateIds won't change
        // aggregateModifiedVideos(lastRun);
        // check "Template" table, for new templates
        aggregateNewTemplates(lastRun);
        now = now.truncatedTo(ChronoUnit.MICROS);
        timerExecution.setLastExecutionTime(now);
        entityManager.merge(timerExecution);
        Log.info("Done aggregating template_data in 'stats' schema.");
    }

    private void aggregateNewTemplates(Instant lastRun) {
        List<Template> templates = entityManager.createQuery(
                """
                    select t from Template t where t.modifiedTime > :lastRun
                    """, Template.class
            )
            .setParameter("lastRun", lastRun)
            .getResultList();
        Log.info("Found " + templates.size() + " modified Templates for Template aggregation.");
        if (templates.isEmpty()) {
            return;
        }
        Map<Long, Template> templateIdToTemplate = templates.stream()
            .collect(Collectors.toMap(Template::getId, template -> template));
        Set<Long> idsOfModifiedTemplate = templateIdToTemplate.keySet();
        List<TemplateData> existingTemplateData =
            entityManager.createQuery(TEMPLATE_DATA_BY_TEMPLATE_IDS, TemplateData.class)
                .setParameter("ids", idsOfModifiedTemplate)
                .getResultList();
        for (var templateData : existingTemplateData) {
            Long templateId = templateData.getId();
            Template template = templateIdToTemplate.get(templateId);
            templateData.copyFromTemplate(template);
            entityManager.merge(templateData);
        }
        entityManager.flush();
        entityManager.clear();
        Set<Long> existingTemplate = existingTemplateData.stream()
            .map(TemplateData::getId)
            .collect(Collectors.toSet());
        existingTemplate.forEach(templateIdToTemplate::remove);
        List<Template> newTemplates = new ArrayList<>(templateIdToTemplate.values());
        try {
            aggregateTemplates(newTemplates);
            createVideoTemplateMap(templateIdToTemplate.keySet());
        } catch (SQLException e) {
            Log.error("Failed to aggregate new Templates, size : " + newTemplates.size(), e);
            throw new RuntimeException(e);
        }
    }

    private void createVideoTemplateMap(Set<Long> newTemplateIds) throws SQLException {
        if (newTemplateIds.isEmpty()) {
            return;
        }
        List<TemplateData> newlyCreatedTemplateData =
            entityManager.createQuery(TEMPLATE_DATA_BY_TEMPLATE_IDS, TemplateData.class)
                .setParameter("ids", newTemplateIds)
                .getResultList();
        var videoIdAndTemplateIds = new HashSet<VideoIdAndTemplateId>();
        for (var templateData : newlyCreatedTemplateData) {
            long templateId = templateData.getId();
            long videoId = templateData.getOriginalVideoId();
            videoIdAndTemplateIds.add(new VideoIdAndTemplateId(videoId, templateId));
        }
        String inClause = getInClauseForVideoIdAndTemplateIds(videoIdAndTemplateIds);
        try (var mainConnection = mainDataSource.getConnection();
             var mainStatement = mainConnection.createStatement();
             var resultSet = mainStatement.executeQuery(CHECK_VIDEO_TEMPLATE_MAP_EXISTENCE.replace(
                 "IN_CLAUSE",
                 inClause
             ))) {
            while (resultSet.next()) {
                long videoId = resultSet.getLong("video_id");
                long templateId = resultSet.getLong("template_id");
                VideoIdAndTemplateId videoIdAndTemplateId = new VideoIdAndTemplateId(videoId, templateId);
                videoIdAndTemplateIds.remove(videoIdAndTemplateId);
            }
        } catch (SQLException e) {
            Log.warnf(e, "Failed to check existence of VideoTemplateMap, inClause : %s", inClause);
            return;
        }
        for (var VideoIdAndTemplateId : videoIdAndTemplateIds) {
            Log.infof(
                "Creating VideoTemplateMap in aggregateNewTemplates for videoId : %s, templateId : %s",
                VideoIdAndTemplateId.getVideoId(),
                VideoIdAndTemplateId.getTemplateId()
            );
            entityManager.persist(new VideoTemplateMap(
                VideoIdAndTemplateId.getVideoId(),
                VideoIdAndTemplateId.getTemplateId(),
                Instant.now()
            ));
        }
        updateTemplateUsageCount(videoIdAndTemplateIds);
    }

    private static String getInClauseForVideoIdAndTemplateIds(Set<VideoIdAndTemplateId> videoIdAndTemplateIds) {
        return String.join(
            ",", videoIdAndTemplateIds.stream()
                .map(videoIdAndTemplateId -> "(" + videoIdAndTemplateId.getVideoId() + ","
                    + videoIdAndTemplateId.getTemplateId() + ")")
                .collect(Collectors.toSet())
        );
    }

    private void aggregateModifiedVideos(Instant lastRun) {
        try (var connection = mainDataSource.getConnection();
             var preparedStatement = connection.prepareStatement(MODIFIED_VIDEOS)) {
            preparedStatement.setTimestamp(1, Timestamp.from(lastRun));
            Map<VideoIdAndTemplateId, Instant> changedVideos = new HashMap<>();
            try (var resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    long id = resultSet.getLong("id");
                    Long[] templateIds = (Long[]) resultSet.getArray("templateIds").getArray();
                    Instant modifiedTime = resultSet.getTimestamp("modifiedTime").toInstant();
                    for (long templateId : templateIds) {
                        changedVideos.put(new VideoIdAndTemplateId(id, templateId), modifiedTime);
                    }
                }
            }
            Set<VideoIdAndTemplateId> videoIdAndTemplateIds = changedVideos.keySet();
            Log.info("Found " + videoIdAndTemplateIds.size() + " modified Videos for Template aggregation.");
            if (videoIdAndTemplateIds.isEmpty()) {
                return;
            }
            String inClause = getInClauseForVideoIdAndTemplateIds(videoIdAndTemplateIds);
            try (var mainConnection = mainDataSource.getConnection();
                 var mainStatement = mainConnection.createStatement();
                 var resultSet = mainStatement.executeQuery(CHECK_VIDEO_TEMPLATE_MAP_EXISTENCE.replace(
                     "IN_CLAUSE",
                     inClause
                 ))) {
                while (resultSet.next()) {
                    long videoId = resultSet.getLong("video_id");
                    long templateId = resultSet.getLong("template_id");
                    VideoIdAndTemplateId videoIdAndTemplateId = new VideoIdAndTemplateId(videoId, templateId);
                    videoIdAndTemplateIds.remove(videoIdAndTemplateId);
                }
            }
            Log.info("Found " + videoIdAndTemplateIds.size() + " new Videos for Template aggregation.");
            if (videoIdAndTemplateIds.isEmpty()) {
                return;
            }
            for (VideoIdAndTemplateId videoIdAndTemplateId : videoIdAndTemplateIds) {
                entityManager.persist(new VideoTemplateMap(
                    videoIdAndTemplateId.getVideoId(),
                    videoIdAndTemplateId.getTemplateId(),
                    changedVideos.get(videoIdAndTemplateId)
                ));
            }
            entityManager.flush();
            entityManager.clear();
            videoIdAndTemplateIds.stream()
                .collect(Collectors.groupingBy(VideoIdAndTemplateId::getTemplateId, Collectors.counting()))
                .forEach((templateId, count) -> {
                    Log.info("Adding usage count for templateId : " + templateId + ", count : " + count);
                    TemplateData templateData = entityManager.find(TemplateData.class, templateId);
                    templateData.setUsageCount(templateData.getUsageCount() + count);
                });
            entityManager.flush();
            entityManager.clear();
        } catch (SQLException e) {
            Log.error("Failed to aggregate modified Videos for Template.", e);
            throw new RuntimeException(e);
        }
    }

    private void aggregateModifiedMusicControllers(Instant lastRun) {
        try (var connection = mainDataSource.getConnection();
             var preparedStatement = connection.prepareStatement(MODIFIED_MUSIC_CONTROLLERS)) {
            preparedStatement.setTimestamp(1, Timestamp.from(lastRun));
            Map<Long, long[]> changedMusicController = new HashMap<>();
            try (var resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    long eventId = resultSet.getLong("event_id");
                    Long[] externalSongIds = (Long[]) resultSet.getArray("external_track_ids").getArray();
                    changedMusicController.put(
                        eventId,
                        Arrays.stream(externalSongIds).mapToLong(Long::longValue).toArray()
                    );
                }
            }
            Set<Long> eventIds = changedMusicController.keySet();
            Log.info("Found " + eventIds.size() + " modified MusicController for Template aggregation.");
            if (eventIds.isEmpty()) {
                return;
            }
            entityManager.createQuery(
                    "select t from TemplateData t where t.eventId in :eventIds",
                    TemplateData.class
                )
                .setParameter("eventIds", eventIds)
                .getResultStream()
                .forEach(templateData -> {
                    long[] externalSongIds = changedMusicController.get(templateData.getEventId());
                    templateData.setExternalSongIds(externalSongIds);
                });
            entityManager.flush();
            entityManager.clear();
        } catch (SQLException e) {
            Log.error("Failed to aggregate modified MusicController for Template.", e);
            throw new RuntimeException(e);
        }
    }

    private void aggregateModifiedGroups(Instant lastRun) {
        try (var connection = mainDataSource.getConnection();
             var preparedStatement = connection.prepareStatement(MODIFIED_GROUPS)) {
            preparedStatement.setTimestamp(1, Timestamp.from(lastRun));
            Map<Long, CountryAndLanguageAndNickName> changedGroups = new HashMap<>();
            try (var resultSet = preparedStatement.executeQuery()) {
                extractCountryLanguageAndNicknameFromResultSet(changedGroups, resultSet);
            }
            Set<Long> groupIds = changedGroups.keySet();
            Log.info("Found " + groupIds.size() + " modified Groups for Template aggregation.");
            if (groupIds.isEmpty()) {
                return;
            }
            entityManager.createQuery(
                    "select t from TemplateData t where t.creatorId in :groupIds",
                    TemplateData.class
                )
                .setParameter("groupIds", groupIds)
                .getResultStream()
                .forEach(templateData -> {
                    CountryAndLanguageAndNickName countryAndLanguageAndNickName =
                        changedGroups.get(templateData.getCreatorId());
                    templateData.setCountry(countryAndLanguageAndNickName.country());
                    templateData.setLanguage(countryAndLanguageAndNickName.language());
                    templateData.setCreatorNickname(countryAndLanguageAndNickName.nickName());
                });
            entityManager.flush();
            entityManager.clear();
        } catch (SQLException e) {
            Log.error("Failed to aggregate modified Groups for Template.", e);
            throw new RuntimeException(e);
        }
    }

    private static void extractCountryLanguageAndNicknameFromResultSet(
        Map<Long, CountryAndLanguageAndNickName> changedGroups,
        ResultSet resultSet
    ) throws SQLException {
        while (resultSet.next()) {
            long groupId = resultSet.getLong("id");
            String country = resultSet.getString("country");
            String language = resultSet.getString("language");
            String nickName = resultSet.getString("nickName");
            changedGroups.put(groupId, new CountryAndLanguageAndNickName(country, language, nickName));
        }
    }

    private void aggregateModifiedCharacters(Instant lastRun) {
        try (var connection = mainDataSource.getConnection();
             var preparedStatement = connection.prepareStatement(MODIFIED_CHARACTERS)) {
            preparedStatement.setTimestamp(1, Timestamp.from(lastRun));
            Map<Long, String> changedCharacters = new HashMap<>();
            try (var resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    long groupId = resultSet.getLong("groupId");
                    String files = resultSet.getString("files");
                    changedCharacters.put(groupId, files);
                }
            }
            Set<Long> groupIds = changedCharacters.keySet();
            Log.info("Found " + groupIds.size() + " modified Characters for Template aggregation.");
            if (groupIds.isEmpty()) {
                return;
            }
            entityManager.createQuery(
                    "select t from TemplateData t where t.creatorId in :groupIds",
                    TemplateData.class
                )
                .setParameter("groupIds", groupIds)
                .getResultStream()
                .forEach(templateData -> {
                    String files = changedCharacters.get(templateData.getCreatorId());
                    templateData.setCreatorMainCharacterFiles(files);
                });
            entityManager.flush();
            entityManager.clear();
        } catch (SQLException e) {
            Log.error("Failed to aggregate modified Characters for Template.", e);
            throw new RuntimeException(e);
        }
    }

    private void aggregateModifiedUsers(Instant lastRun) {
        try (var connection = mainDataSource.getConnection();
             var preparedStatement = connection.prepareStatement(MODIFIED_USERS)) {
            preparedStatement.setTimestamp(1, Timestamp.from(lastRun));
            Map<Long, CharacterInfo> changedCharacters = new HashMap<>();
            try (var resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    long groupId = resultSet.getLong("groupId");
                    Long characterId =
                        resultSet.getLong("characterId") == -1 ? null : resultSet.getLong("characterId");
                    String files = resultSet.getString("files");
                    CharacterInfo characterInfo = new CharacterInfo(characterId, files);
                    changedCharacters.put(groupId, characterInfo);
                }
            }
            Set<Long> groupIds = changedCharacters.keySet();
            Log.info("Found " + groupIds.size() + " modified Users for Template aggregation.");
            if (groupIds.isEmpty()) {
                return;
            }
            entityManager.createQuery("select t from TemplateData t where t.creatorId in :groupIds", TemplateData.class)
                .setParameter("groupIds", groupIds)
                .getResultStream()
                .forEach(templateData -> {
                    CharacterInfo characterInfo = changedCharacters.get(templateData.getCreatorId());
                    templateData.setCreatorMainCharacterId(characterInfo.characterId());
                    templateData.setCreatorMainCharacterFiles(characterInfo.files());
                });
            entityManager.flush();
            entityManager.clear();
        } catch (SQLException e) {
            Log.error("Failed to aggregate modified Users for Template.", e);
            throw new RuntimeException(e);
        }

    }

    public void handleGroupChangedMessage(GroupChangedMessage groupChanged) {
        long groupId = groupChanged.groupId();
        // only mainCharacterId could be changed for now, let's make use of that.
        Long mainCharacterId = groupChanged.mainCharacterId();
        List<TemplateData> templates = entityManager.createQuery(
                "select t from TemplateData t where t.creatorId = :groupId", TemplateData.class)
            .setParameter("groupId", groupId)
            .getResultList();
        for (var template : templates) {
            if (template.getCreatorMainCharacterId() == null && mainCharacterId == null) {
                break;
            }
            if (template.getCreatorMainCharacterId() != null && !template.getCreatorMainCharacterId()
                .equals(mainCharacterId)) {
                template.setCreatorMainCharacterId(mainCharacterId);
            }
        }
    }

    public void handleTemplateUpdatedMessage(TemplateUpdatedMessage message) {
        long templateId = message.templateId();
        Template template = entityManager.find(Template.class, templateId);
        try {
            aggregateTemplates(List.of(template));
        } catch (SQLException e) {
            Log.warnf(e, "Failed to aggregate Template for templateId : %s", templateId);
            throw new RuntimeException(e);
        }
    }

    public void handleVideoTemplateMappingMessage(VideoTemplateMappingMessage videoTemplateMapping) {
        long videoId = videoTemplateMapping.videoId();
        long[] newTemplateIds = videoTemplateMapping.newTemplateIds();
        long[] oldTemplateIds = videoTemplateMapping.oldTemplateIds();
        Set<Long> templateMappingsToDelete = new HashSet<>();
        Set<Long> newTemplateIdSet = Arrays.stream(newTemplateIds).boxed().collect(Collectors.toSet());
        Set<Long> templateMappingsToPersist = new HashSet<>(newTemplateIdSet);
        if (oldTemplateIds != null) {
            for (long oldTemplateId : oldTemplateIds) {
                if (!newTemplateIdSet.contains(oldTemplateId)) {
                    templateMappingsToDelete.add(oldTemplateId);
                } else {
                    templateMappingsToPersist.remove(oldTemplateId);
                }
            }
        }
        if (!templateMappingsToDelete.isEmpty()) {
            Log.infof("Deleting %s template mappings for videoId : %s", templateMappingsToDelete, videoId);
            entityManager.createQuery(
                    "delete from VideoTemplateMap v where v.videoId = :videoId and v.templateId in :templateIds"
                )
                .setParameter("videoId", videoId)
                .setParameter("templateIds", templateMappingsToDelete)
                .executeUpdate();
        }
        if (!templateMappingsToPersist.isEmpty()) {
            Log.infof("Persisting %s template mappings for videoId : %s", templateMappingsToPersist, videoId);
            for (long templateId : templateMappingsToPersist) {
                try {
                    entityManager.persist(new VideoTemplateMap(videoId, templateId, Instant.now()));
                } catch (EntityExistsException e) {
                    Log.infof(
                        e,
                        "VideoTemplateMap already exists for videoId : %s, templateId : %s",
                        videoId,
                        templateId
                    );
                }
            }
        }
        List<Long> notFoundTemplateIds = new ArrayList<>();
        for (long templateId : templateMappingsToPersist) {
            TemplateData templateData = entityManager.find(TemplateData.class, templateId);
            if (templateData == null) {
                Log.info("TemplateData not found for templateId when increasing usageCount : " + templateId
                    + ", will try to aggregate it.");
                notFoundTemplateIds.add(templateId);
                continue;
            }
            templateData.setUsageCount(templateData.getUsageCount() + 1);
        }
        for (long templateId : templateMappingsToDelete) {
            TemplateData templateData = entityManager.find(TemplateData.class, templateId);
            if (templateData == null) {
                Log.info("TemplateData not found for templateId when decreasing usageCount : " + templateId
                    + ", trying to aggregate it.");
                notFoundTemplateIds.add(templateId);
                continue;
            }
            long usageCount = templateData.getUsageCount() - 1;
            templateData.setUsageCount(Math.max(usageCount, 0));
        }
        if (!notFoundTemplateIds.isEmpty()) {
            Log.infof(
                "TemplateData not found when handling VideoTemplateMappingMessage, templateIds : %s, will aggregate",
                notFoundTemplateIds
            );
            List<Template> templates = fetchTemplatesWithRetry(notFoundTemplateIds);
            try {
                aggregateTemplates(templates);
            } catch (SQLException e) {
                Log.warnf(e, "Failed to aggregate Templates for templateIds : %s", notFoundTemplateIds);
                throw new RuntimeException(e);
            }
        }
    }

    private List<Template> fetchTemplatesWithRetry(Collection<Long> templateIds) {
        List<Template> templates = entityManager.createQuery(TEMPLATE_BY_IDS, Template.class)
            .setParameter("ids", templateIds)
            .getResultList();
        int retryCount = 0;
        while (templates.isEmpty()) {
            if (retryCount > FETCH_RETRIES) {
                Log.errorf("Failed to fetch Templates for templateIds with retries : %s", templateIds);
                break;
            }
            retryCount++;
            try {
                Thread.sleep(RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            templates = entityManager.createQuery(
                    """
                        select t from Template t where t.id in (:ids)
                        """, Template.class
                ).setParameter("ids", templateIds)
                .getResultList();
        }
        return templates;
    }

    @Lock(value = Lock.Type.WRITE, time = 30, unit = TimeUnit.SECONDS)
    public void bootstrapOneTemplate(long templateId) {
        // aggregateTemplateData();
        Log.info("Bootstrapping data in 'stats' schema for Template : " + templateId);
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("delete from stats.template_data where id = " + templateId);
            statement.execute("delete from stats.video_template_map where template_id = " + templateId);
        } catch (SQLException e) {
            Log.error("Failed to cleanup tables in 'stats' schema for template with id: " + templateId, e);
            throw new RuntimeException(e);
        }
        Template template = entityManager.find(Template.class, templateId);
        if (template == null) {
            Log.error("Template not found for id : " + templateId);
            return;
        }
        try {
            aggregateTemplates(List.of(template));
        } catch (SQLException e) {
            Log.error("Failed to bootstrap template : " + templateId, e);
        }
        Log.info("Done bootstrapping data in 'stats' schema for Template : " + templateId);
    }

    @Lock(value = Lock.Type.WRITE, time = 1, unit = TimeUnit.MINUTES)
    public void bootstrap() {
        Log.info("Bootstrapping data in 'stats' schema for Template.");
        recordTimerExecution();
        cleanup();
        bootstrapVideoTemplateMap();
        bootstrapTemplateData();
        Log.info("Done bootstrapping data in 'stats' schema for Template.");
    }

    @Override
    protected String getTimerName() {
        return TIMER_NAME;
    }

    private void cleanup() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate table stats.template_data");
            statement.execute("truncate table stats.video_template_map");
        } catch (SQLException e) {
            String message = "Failed to cleanup Template aggregation tables in 'stats' schema.";
            Log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    private void bootstrapVideoTemplateMap() {
        try (var videoConnection = mainDataSource.getConnection();
             var videoStatement = videoConnection.prepareStatement(INFO_FROM_VIDEO)) {
            videoStatement.setFetchSize(FETCH_SIZE);
            try (var resultSet = videoStatement.executeQuery()) {
                int count = 0;
                while (resultSet.next()) {
                    long id = resultSet.getLong("id");
                    Long[] templateIds = (Long[]) resultSet.getArray("templateIds").getArray();
                    Set<Long> templateIdSet = new HashSet<>(List.of(templateIds));
                    Instant modifiedTime = resultSet.getTimestamp("modifiedTime").toInstant();
                    for (long templateId : templateIdSet) {
                        try {
                            entityManager.persist(new VideoTemplateMap(id, templateId, modifiedTime));
                        } catch (EntityExistsException e) {
                            Log.error(
                                "Failed to persist VideoTemplateMap, video_id : " + id + ", templateId : " + templateId,
                                e
                            );
                        }
                    }
                    count++;
                    if (count % (FETCH_SIZE / 5) == 0) {
                        entityManager.flush();
                        entityManager.clear();
                    }
                }
                entityManager.flush();
                entityManager.clear();
            }
        } catch (SQLException e) {
            Log.error("Failed to bootstrap 'stats.video_template_map' table", e);
        }
    }

    private void bootstrapTemplateData() {
        try {
            List<Template> templates = entityManager.createQuery(ALL_TEMPLATES, Template.class).getResultList();
            aggregateTemplates(templates);
        } catch (Exception e) {
            Log.error("Failed to bootstrap 'stats.template_data' table", e);
        }
    }

    @Lock(value = Lock.Type.WRITE, time = 2, unit = TimeUnit.SECONDS)
    public void aggregateTemplate(long templateId) {
        Template template = entityManager.find(Template.class, templateId);
        if (template == null) {
            Log.error("Template not found for id : " + templateId);
            return;
        }
        try {
            aggregateTemplates(List.of(template));
        } catch (SQLException e) {
            Log.error("Failed to aggregate Template for templateId : " + templateId, e);
        }
    }

    private void updateTemplateUsageCount(Set<VideoIdAndTemplateId> videoIdAndTemplateIds) throws SQLException {
        Set<Long> templateIds = videoIdAndTemplateIds.stream()
            .map(VideoIdAndTemplateId::getTemplateId)
            .collect(Collectors.toSet());
        Log.infof("Updating usage count for %s templates", templateIds);
        if (templateIds.isEmpty()) {
            return;
        }
        try (var mainConnection = mainDataSource.getConnection();
             var mainStatement = mainConnection.createStatement()) {
            String inClause = String.join(",", templateIds.stream().map(String::valueOf).collect(Collectors.toSet()));
            try (var resultSet = mainStatement.executeQuery(TEMPLATE_USAGE_COUNT_BY_TEMPLATE_IDS.replace(
                "IN_CLAUSE",
                inClause
            ))) {
                Map<Long, Long> templateIdToUsageCount = new HashMap<>();
                while (resultSet.next()) {
                    long templateId = resultSet.getLong("template_id");
                    long count = resultSet.getLong("count");
                    templateIdToUsageCount.put(templateId, count);
                }
                for (var templateId : templateIds) {
                    long usageCount = templateIdToUsageCount.getOrDefault(templateId, 0L);
                    TemplateData templateData = entityManager.find(TemplateData.class, templateId);
                    templateData.setUsageCount(usageCount);
                }
            }
        }
    }

    private void aggregateTemplates(List<Template> templates) throws SQLException {
        if (templates.isEmpty()) {
            Log.info("Aggregate Templates called with empty list.");
            return;
        }
        try (var mainConnection = mainDataSource.getConnection();
             var mainStatement = mainConnection.createStatement()) {
            Set<Long> allGroupIds = templates.stream().map(Template::getCreatorId).collect(Collectors.toSet());
            Set<Long> allEventIds = templates.stream().map(Template::getEventId).collect(Collectors.toSet());
            Set<Long> allTemplateSubCategoryIds =
                templates.stream().map(Template::getTemplateSubCategoryId).collect(Collectors.toSet());
            Set<Long> allTemplateIds = templates.stream().map(Template::getId).collect(Collectors.toSet());
            Map<Long, CountryAndLanguageAndNickName> countryAndLanguageAndNickName =
                getLongCountryAndLanguageAndNickNameMap(mainStatement, allGroupIds);
            Map<Long, Long> templateSubCategoryToTemplateCategory =
                getTemplateSubCategoryToTemplateCategoryMap(mainStatement, allTemplateSubCategoryIds);
            Map<Long, CharacterInfo> groupIdToCharacterInfoMap =
                getGroupIdToCharacterInfoMap(mainStatement, allGroupIds);
            Map<Long, Long> templateUsageCount = getTemplateUsageCount(mainStatement, allTemplateIds);
            Map<Long, long[]> eventIdToExternalSongIdsMap =
                getEventIdToExternalSongIdsMap(mainStatement, allEventIds);
            Map<Long, long[]> eventIdToSongIdsMap = getEventIdToSongIdsMap(mainStatement, allEventIds);
            Map<Long, Long> eventIdToLevelIdMap = getEventIdToLevelIdMap(mainStatement, allEventIds);
            Map<Long, Long> eventIdToVideoIdMap = getEventIdToVideoIdMap(eventIdToLevelIdMap);
            int count = 0;
            for (var template : templates) {
                var templateData = TemplateData.newCopyFromTemplate(template);
                templateData.setCountry(countryAndLanguageAndNickName.get(template.getCreatorId()).country());
                templateData.setLanguage(countryAndLanguageAndNickName.get(template.getCreatorId()).language());
                templateData.setCreatorNickname(countryAndLanguageAndNickName.get(template.getCreatorId())
                    .nickName());
                templateData.setTemplateCategoryId(templateSubCategoryToTemplateCategory.get(template.getTemplateSubCategoryId()));
                templateData.setCreatorMainCharacterId(groupIdToCharacterInfoMap.get(template.getCreatorId())
                    .characterId());
                templateData.setCreatorMainCharacterFiles(groupIdToCharacterInfoMap.get(template.getCreatorId())
                    .files());
                templateData.setUsageCount(templateUsageCount.getOrDefault(template.getId(), 0L));
                templateData.setExternalSongIds(requireNonNullElse(
                    eventIdToExternalSongIdsMap.get(template.getEventId()),
                    new long[0]
                ));
                templateData.setSongIds(requireNonNullElse(
                    eventIdToSongIdsMap.get(template.getEventId()),
                    new long[0]
                ));
                templateData.setLevelId(eventIdToLevelIdMap.get(template.getEventId()));
                templateData.setOriginalVideoId(eventIdToVideoIdMap.get(template.getEventId()));
                templateData.setStatsUpdatedTimestamp(Instant.now());
                count++;
                entityManager.merge(templateData);
                if (count % (FETCH_SIZE / 5) == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            entityManager.flush();
            entityManager.clear();
        }
    }

    private Map<Long, Long> getEventIdToVideoIdMap(Map<Long, Long> eventIdToLevelIdMap) throws SQLException {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery(VIDEO_ID_FROM_LEVEL_ID.replace(
                 "IN_CLAUSE",
                 String.join(
                     ",",
                     eventIdToLevelIdMap.values().stream().map(String::valueOf).collect(Collectors.toSet())
                 )
             ))) {
            Map<Long, Long> levelIdToVideoIdMap = new HashMap<>();
            while (result.next()) {
                long levelId = result.getLong("level_id");
                long videoId = result.getLong("video_id");
                levelIdToVideoIdMap.put(levelId, videoId);
            }
            Map<Long, Long> eventIdToVideoIdMap = new HashMap<>();
            for (var entry : eventIdToLevelIdMap.entrySet()) {
                var levelId = entry.getValue();
                var eventId = entry.getKey();
                var videoId = levelIdToVideoIdMap.get(levelId);
                eventIdToVideoIdMap.put(eventId, videoId);
            }
            return eventIdToVideoIdMap;
        }
    }

    private Map<Long, Long> getEventIdToLevelIdMap(Statement mainStatement, Set<Long> allEventIds) throws SQLException {
        //noinspection SqlSourceToSinkFlow
        try (var resultSet = mainStatement.executeQuery(INFO_FROM_EVENT.replace(
            "IN_CLAUSE",
            String.join(",", allEventIds.stream().map(String::valueOf).collect(Collectors.toSet()))
        ))) {
            Map<Long, Long> eventIdToLevelId = new HashMap<>();
            while (resultSet.next()) {
                long eventId = resultSet.getLong("id");
                long levelId = resultSet.getLong("levelId");
                eventIdToLevelId.put(eventId, levelId);
            }
            return eventIdToLevelId;
        }
    }

    private static Map<Long, long[]> getEventIdToExternalSongIdsMap(
        Statement mainStatement, Set<Long> allEventIds
    ) throws SQLException {
        //noinspection SqlSourceToSinkFlow
        try (var resultSet = mainStatement.executeQuery(EXTERNAL_SONG_IDS_FROM_MUSIC_CONTROLLER.replace(
            "IN_CLAUSE",
            String.join(",", allEventIds.stream().map(String::valueOf).collect(Collectors.toSet()))
        ))) {
            Map<Long, long[]> eventIdToExternalSongIds = new HashMap<>();
            while (resultSet.next()) {
                long eventId = resultSet.getLong("event_id");
                Long[] externalSongIds = (Long[]) resultSet.getArray("external_track_ids").getArray();
                eventIdToExternalSongIds.put(
                    eventId,
                    Arrays.stream(externalSongIds).mapToLong(Long::longValue).toArray()
                );
            }
            return eventIdToExternalSongIds;
        }
    }

    private Map<Long, long[]> getEventIdToSongIdsMap(Statement mainStatement, Set<Long> allEventIds)
        throws SQLException {
        //noinspection SqlSourceToSinkFlow
        try (var resultSet = mainStatement.executeQuery(SONG_IDS_FROM_MUSIC_CONTROLLER.replace(
            "IN_CLAUSE",
            String.join(",", allEventIds.stream().map(String::valueOf).collect(Collectors.toSet()))
        ))) {
            Map<Long, long[]> eventIdToSongIds = new HashMap<>();
            while (resultSet.next()) {
                long eventId = resultSet.getLong("event_id");
                Long[] songIds = (Long[]) resultSet.getArray("song_ids").getArray();
                eventIdToSongIds.put(
                    eventId,
                    Arrays.stream(songIds).mapToLong(Long::longValue).toArray()
                );
            }
            return eventIdToSongIds;
        }
    }

    private static Map<Long, Long> getTemplateUsageCount(Statement mainStatement, Set<Long> templateIds) {
        Map<Long, Long> templateUsageCount = new HashMap<>();
        String inClause = String.join(",", templateIds.stream().map(String::valueOf).collect(Collectors.toSet()));
        try (var resultSet = mainStatement.executeQuery(TEMPLATE_USAGE_COUNT_BY_TEMPLATE_IDS.replace(
            "IN_CLAUSE",
            inClause
        ))) {
            while (resultSet.next()) {
                long templateId = resultSet.getLong("template_id");
                long count = resultSet.getLong("count");
                templateUsageCount.put(templateId, count);
            }
        } catch (SQLException e) {
            Log.error("Failed to get template usage count.", e);
        }
        return templateUsageCount;
    }

    private static Map<Long, CountryAndLanguageAndNickName> getLongCountryAndLanguageAndNickNameMap(
        Statement mainStatement, Set<Long> allGroupIds
    ) throws SQLException {
        //noinspection SqlSourceToSinkFlow
        try (var resultSet = mainStatement.executeQuery(INFO_FROM_GROUP_TABLE.replace(
            "IN_CLAUSE",
            String.join(",", allGroupIds.stream().map(String::valueOf).collect(Collectors.toSet()))
        ))) {
            Map<Long, CountryAndLanguageAndNickName> countryAndLanguage = new HashMap<>();
            extractCountryLanguageAndNicknameFromResultSet(countryAndLanguage, resultSet);
            return countryAndLanguage;
        }
    }

    private static Map<Long, Long> getTemplateSubCategoryToTemplateCategoryMap(
        Statement mainStatement, Set<Long> allTemplateSubCategoryIds
    ) throws SQLException {
        //noinspection SqlSourceToSinkFlow
        try (var resultSet = mainStatement.executeQuery(INFO_FROM_TEMPLATE_SUB_CATEGORY.replace(
            "IN_CLAUSE",
            String.join(",", allTemplateSubCategoryIds.stream().map(String::valueOf).collect(Collectors.toSet()))
        ))) {
            Map<Long, Long> templateCategories = new HashMap<>();
            while (resultSet.next()) {
                long templateSubCategoryId = resultSet.getLong("id");
                long templateCategoryId = resultSet.getLong("templateCategoryId");
                templateCategories.put(templateSubCategoryId, templateCategoryId);
            }
            return templateCategories;
        }
    }

    private static Map<Long, CharacterInfo> getGroupIdToCharacterInfoMap(
        Statement mainStatement, Set<Long> allGroupIds
    ) throws SQLException {
        //noinspection SqlSourceToSinkFlow
        try (var resultSet = mainStatement.executeQuery(INFO_FROM_USER_TABLE.replace(
            "IN_CLAUSE",
            String.join(",", allGroupIds.stream().map(String::valueOf).collect(Collectors.toSet()))
        ))) {
            Map<Long, CharacterInfo> characterInfo = new HashMap<>();
            while (resultSet.next()) {
                long groupId = resultSet.getLong("groupId");
                Long characterId = resultSet.getLong("characterId") == -1 ? null : resultSet.getLong("characterId");
                String files = resultSet.getString("files");
                characterInfo.put(groupId, new CharacterInfo(characterId, files));
            }
            return characterInfo;
        }
    }

    @Lock(value = Lock.Type.WRITE, time = 30, unit = TimeUnit.SECONDS)
    public void bootstrapTemplateSongIds() {
        Log.info("Bootstrapping song_ids in 'stats' schema for Template.");
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            var initialiseSongIds = """
                with event_song as (
                    select t.event_id, COALESCE(array_agg(distinct m."SongId") filter (where m."SongId" is not null), '{}') as song_ids
                        from stats.template_data t inner join "MusicController" m on t.event_id = m."EventId"
                        group by t.event_id
                )
                update stats.template_data t set song_ids = event_song.song_ids
                    from event_song where t.event_id = event_song.event_id;
                """;
            var count = statement.executeUpdate(initialiseSongIds);
            Log.info("Updated " + count + " rows in 'stats.template_data' with song_ids.");
        } catch (SQLException e) {
            Log.error("Failed to bootstrap song_ids in 'stats' schema for template", e);
            throw new RuntimeException(e);
        }
        Log.info("Done bootstrapping song_ids in 'stats' schema for Template.");
    }
}
