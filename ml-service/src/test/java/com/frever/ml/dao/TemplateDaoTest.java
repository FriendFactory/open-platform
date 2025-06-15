package com.frever.ml.dao;

import static com.frever.ml.utils.Constants.GROUP_IDS_TO_EXCLUDE;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TemplateDaoTest extends DaoTestBase {
    @Inject
    TemplateDao templateDao;
    @Inject
    GeoClusterDao geoClusterDao;

    @Test
    public void testGetCuratedTemplates() {
        prepareTemplates();
        var result = templateDao.getCuratedTemplates();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        cleanUpTemplates();
    }

    private void cleanUpTemplates() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"Template\"");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void prepareTemplates() {
        var insertTemplates = """
            INSERT INTO "Template" (
                "Id",
                "IsDeleted",
                "ReadinessId",
                "CharacterCount",
                "TrendingSortingOrder"
            ) VALUES (
                DEFAULT,
                false,
                2,
                100,
                1
            ),
            (
                DEFAULT,
                false,
                1,
                200,
                2
            ),
            (
                DEFAULT,
                true,
                2,
                300,
                3
            )
            """;
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertTemplates);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGetVideosBasedOnTemplateRanking() {
        prepareVideos();
        try {
            var geoClusters = geoClusterDao.getGeoClusters();
            var result = templateDao.getVideoInfoBasedOnTemplateRanking(
                1,
                1,
                List.of(1L, 2L),
                Collections.emptyList(),
                GROUP_IDS_TO_EXCLUDE,
                geoClusters.getFirst()
            );
            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals(2, result.getFirst().videoId());
        } finally {
            cleanUpVideos();
        }
    }

    private void prepareVideos() {
        var insertView = """
            insert into "Views" (
                "UserId",
                "VideoId",
                "Time"
            ) values (
                1,
                3,
                now()
            )
            """;
        var insertLike = """
            insert into "Likes" (
                "UserId",
                "VideoId",
                "Time"
            ) values (
                1,
                4,
                now()
            )
            """;
        var insertVideo = """
            insert into "Video" (
                "Id",
                "GroupId",
                "CreatedTime",
                "ExternalSongIds",
                "SongInfo",
                "UserSoundInfo",
                "StartListItem",
                "Country",
                "Language",
                "IsDeleted",
                "Access",
                "PublishTypeId",
                "GeneratedTemplateId"
            ) values (
                DEFAULT,
                2,
                now(),
                '{1, 2}',
                '[{"Id":1,"Artist":"Frever Sounds","Title":"Magic candy","IsExternal":false}]',
                '[{"Id" : 1, "Name" : "emma1", "EventId" : 1},{"Id" : 2, "Name" : "emma2", "EventId" : 2}]',
                1,
                'swe',
                'eng',
                false,
                'Public',
                1,
                1
            ),
            (
                DEFAULT,
                2,
                now() - interval '1 day',
                '{3, 4}',
                '[{"Id":2,"Artist":"Frever Sounds","Title":"Magic candy","IsExternal":false}]',
                '[{"Id" : 3, "Name" : "emma1", "EventId" : 1},{"Id" : 4, "Name" : "emma2", "EventId" : 2}]',
                null,
                'gbr',
                'eng',
                false,
                'Public',
                1,
                1
            )
            """;
        var insertGeoCluster = """
            insert into "GeoCluster" (
                "Id",
                "Priority",
                "IncludeVideoFromCountry",
                "IncludeVideoWithLanguage"
            ) values (
                DEFAULT,
                10,
                '{swe, gbr}',
                '{eng}'
            ),
            (
                DEFAULT,
                9,
                '{swe}',
                '{eng}'
            )
            """;
        var insertTemplateRankings = """
            insert into stats.template_ranking (
                template_id,
                rank
            ) values (
                1,
                1
            ),
            (
                2,
                2
            )
            """;
        var insertTemplateData = """
            insert into stats.template_data (
                id,
                original_video_id,
                is_deleted,
                readiness_id
            ) values (
                1,
                1,
                false,
                2
            ),
            (
                2,
                2,
                false,
                2
            )
            """;
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertView);
            statement.execute(insertLike);
            statement.execute(insertVideo);
            statement.execute(insertGeoCluster);
            statement.execute(insertTemplateRankings);
            statement.execute(insertTemplateData);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUpVideos() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate \"Views\" restart identity");
            statement.execute("truncate \"Likes\" restart identity");
            statement.execute("truncate \"Video\" restart identity");
            statement.execute("truncate \"GeoCluster\" restart identity");
            statement.execute("truncate stats.template_ranking restart identity");
            statement.execute("truncate stats.template_data restart identity");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }
}
