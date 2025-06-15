package com.frever.ml.dao;

import static com.frever.ml.utils.Constants.GROUP_IDS_TO_EXCLUDE;

import com.frever.ml.dto.GeoLocation;
import com.frever.ml.dto.UserInfo;
import com.frever.ml.dto.VideoIdAndDistance;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class VideoDaoTest extends DaoTestBase {
    @Inject
    VideoDao videoDao;
    @Inject
    GeoClusterDao geoClusterDao;

    @Test
    public void testGetLikedAccounts() {
        prepareLikedAccounts();
        try {
            var result = videoDao.getLikedAccounts(1, 1);
            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.size());
            var first = result.getFirst();
            Assertions.assertEquals(1, first.groupId());
        } finally {
            cleanUpLikedAccounts();
        }
    }

    private void prepareLikedAccounts() {
        var insertLike = """
            insert into "Likes" (
                "UserId",
                "VideoId",
                "Time"
            ) values (
                1,
                1,
                now()
            )
            """;
        var insertUser = """
            insert into "User" (
                "Id",
                "MainGroupId"
            ) values (
                DEFAULT,
                1
            )
            """;
        var insertVideo = """
            insert into "Video" (
                "Id",
                "GroupId",
                "CreatedTime",
                "ExternalSongIds",
                "SongInfo",
                "Access"
            ) values (
                DEFAULT,
                1,
                now(),
                '{}',
                '{}',
                'Public'
            )
            """;
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertUser);
            statement.execute(insertVideo);
            statement.execute(insertLike);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUpLikedAccounts() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate \"Likes\" restart identity");
            statement.execute("truncate \"User\" restart identity");
            statement.execute("truncate \"Video\" restart identity");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGetCandidateVideos() {
        prepareCandidateVideos();
        try {
            var geoClusters = geoClusterDao.getGeoClusters();
            Assertions.assertNotNull(geoClusters);
            Assertions.assertEquals(2, geoClusters.size());
            Assertions.assertTrue(geoClusters.getFirst().includeVideoFromCountry().contains("gbr"));
            var coldStartVideos =
                videoDao.getCandidateVideos(
                    1,
                    1,
                    List.of(3L, 4L),
                    List.of(3L),
                    GROUP_IDS_TO_EXCLUDE,
                    geoClusters.getFirst(),
                    true
                );
            Assertions.assertNotNull(coldStartVideos);
            Assertions.assertEquals(1, coldStartVideos.size());
            var coldStartVideo = coldStartVideos.getFirst();
            Assertions.assertAll(
                () -> Assertions.assertTrue(coldStartVideo.externalSongIds().contains(1L)),
                () -> Assertions.assertTrue(coldStartVideo.externalSongIds().contains(2L)),
                () -> Assertions.assertEquals("swe", coldStartVideo.country()),
                () -> Assertions.assertEquals("eng", coldStartVideo.language()),
                () -> Assertions.assertEquals(1L, coldStartVideo.generatedTemplateId())
            );
            var nonColdStartVideos =
                videoDao.getCandidateVideos(
                    1,
                    1,
                    List.of(3L, 4L),
                    List.of(3L),
                    GROUP_IDS_TO_EXCLUDE,
                    geoClusters.getFirst(),
                    false
                );
            Assertions.assertNotNull(nonColdStartVideos);
            Assertions.assertEquals(2, nonColdStartVideos.size());
            var first = nonColdStartVideos.getFirst();
            Assertions.assertAll(
                () -> Assertions.assertTrue(first.externalSongIds().contains(3L)),
                () -> Assertions.assertTrue(first.externalSongIds().contains(4L)),
                () -> Assertions.assertEquals("gbr", first.country()),
                () -> Assertions.assertEquals("eng", first.language())
            );
            var second = nonColdStartVideos.getLast();
            Assertions.assertAll(
                () -> Assertions.assertTrue(second.externalSongIds().contains(33L)),
                () -> Assertions.assertTrue(second.externalSongIds().contains(44L)),
                () -> Assertions.assertEquals("swe", second.country()),
                () -> Assertions.assertEquals("eng", second.language())
            );
        } finally {
            cleanUpCandidateVideos();
        }
    }

    private void prepareCandidateVideos() {
        var insertView = """
            insert into "Views" (
                "UserId",
                "VideoId",
                "Time"
            ) values (
                1,
                5,
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
                6,
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
                "GeneratedTemplateId",
                "LevelId"
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
                1,
                NULL
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
                1,
                NULL
            ),
            (
                DEFAULT,
                2,
                now() - interval '2 day',
                '{33, 44}',
                '[{"Id":2,"Artist":"Frever Sounds","Title":"Magic candy","IsExternal":false}]',
                '[{"Id" : 3, "Name" : "emma1", "EventId" : 1},{"Id" : 4, "Name" : "emma2", "EventId" : 2}]',
                null,
                'swe',
                'eng',
                false,
                'Public',
                1,
                1,
                1
            ),
            (
                DEFAULT,
                2,
                now() - interval '3 day',
                '{44, 55}',
                '[{"Id":2,"Artist":"Frever Sounds","Title":"Magic candy","IsExternal":false}]',
                '[{"Id" : 3, "Name" : "emma1", "EventId" : 1},{"Id" : 4, "Name" : "emma2", "EventId" : 2}]',
                null,
                'gbr',
                'eng',
                false,
                'Public',
                1,
                1,
                2
            )
            """;
        var insertLevel = """
            insert into "Level" (
                "Id",
                "GroupId"
            ) values (
                DEFAULT,
                2
            ),
            (
                DEFAULT,
                2
            )
            """;
        var insertEvent = """
            insert into "Event" (
                "Id",
                "GroupId",
                "LevelId"
            ) values (
                DEFAULT,
                2,
                1
            ),
            (
                DEFAULT,
                2,
                2
            )
            """;
        var insertCharacterController = """
            insert into "CharacterController" (
                "Id",
                "EventId"
            ) values (
                DEFAULT,
                1
            ),
            (
                DEFAULT,
                2
            )
            """;
        var insertCharacterControllerBodyAnimation = """
            insert into "CharacterControllerBodyAnimation" (
                "Id",
                "CharacterControllerId",
                "PrimaryBodyAnimationId",
                "LowerBodyAnimationId"
            ) values (
                DEFAULT,
                1,
                1,
                NULL
            ),
            (
                DEFAULT,
                2,
                602,
                NULL
            );
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
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertView);
            statement.execute(insertLike);
            statement.execute(insertVideo);
            statement.execute(insertLevel);
            statement.execute(insertEvent);
            statement.execute(insertCharacterController);
            statement.execute(insertCharacterControllerBodyAnimation);
            statement.execute(insertGeoCluster);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUpCandidateVideos() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate \"Views\" restart identity");
            statement.execute("truncate \"Likes\" restart identity");
            statement.execute("truncate \"Video\" restart identity");
            statement.execute("truncate \"GeoCluster\" restart identity");
            statement.execute("truncate \"Level\" restart identity");
            statement.execute("truncate \"Event\" restart identity");
            statement.execute("truncate \"CharacterController\" restart identity");
            statement.execute("truncate \"CharacterControllerBodyAnimation\" restart identity");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGetFollowingVideos() {
        prepareFollowingVideos();
        try {
            var geoClusters = geoClusterDao.getGeoClusters();
            Assertions.assertNotNull(geoClusters);
            Assertions.assertEquals(2, geoClusters.size());
            Assertions.assertTrue(geoClusters.getFirst().includeVideoFromCountry().contains("gbr"));
            var userInfo = new UserInfo(1, 1, "swe", "eng");
            var followingVideos = videoDao.getFollowingVideos(userInfo, List.of(1L, 2L), List.of(3L));
            Assertions.assertNotNull(followingVideos);
            Assertions.assertEquals(1, followingVideos.size());
            var followingVideo = followingVideos.getFirst();
            Assertions.assertAll(
                () -> Assertions.assertTrue(followingVideo.externalSongIds().contains(3L)),
                () -> Assertions.assertTrue(followingVideo.externalSongIds().contains(4L)),
                () -> Assertions.assertEquals("gbr", followingVideo.country()),
                () -> Assertions.assertEquals("eng", followingVideo.language())
            );
        } finally {
            cleanUpFollowingVideos();
        }
    }

    private void prepareFollowingVideos() {
        prepareCandidateVideos();
        var insertFollow = """
            insert into "Follower" (
                "FollowerId",
                "FollowingId",
                "Time"
            ) values (
                1,
                2,
                now()
            )
            """;
        var insertGroup = """
            insert into "Group" (
                "Id",
                "Name",
                "TaxationCountryId",
                "DefaultLanguageId"
            ) values (
                2,
                'Test Group',
                1,
                1
            )
            """;
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertFollow);
            statement.execute(insertGroup);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUpFollowingVideos() {
        cleanUpCandidateVideos();
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate \"Follower\"");
            statement.execute("truncate \"Group\" restart identity");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGetVideoDistanceInfo() {
        prepareVideoDistanceInfo();
        try {
            List<VideoIdAndDistance> videoDistanceInfo =
                videoDao.getVideoDistanceInfo(List.of(1L, 2L), new GeoLocation(1, 1));
            Assertions.assertAll(
                () -> Assertions.assertEquals(2, videoDistanceInfo.size()),
                () -> Assertions.assertEquals(1, videoDistanceInfo.getFirst().videoId()),
                () -> Assertions.assertEquals(0.0, videoDistanceInfo.getFirst().distance()),
                () -> Assertions.assertEquals(2, videoDistanceInfo.getLast().videoId()),
                () -> Assertions.assertTrue(Math.abs(videoDistanceInfo.getLast().distance() - 157) < 1)
            );
        } finally {
            cleanUpVideoTable();
        }
    }

    private void prepareVideoDistanceInfo() {
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
                "Location"
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
                'POINT(1 1)'
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
                'POINT(1.001 1.001)'
            )
            """;
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertVideo);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUpVideoTable() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate \"Video\" restart identity");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGetTemplateIdsFromVideoIds() {
        prepareVideoIdsFromTemplateIds();
        try {
            List<Long> videoIds = videoDao.getTemplateIdsFromVideoIds(List.of(1L, 2L));
            Assertions.assertAll(
                () -> Assertions.assertEquals(2, videoIds.size()),
                () -> Assertions.assertTrue(videoIds.contains(1L)),
                () -> Assertions.assertTrue(videoIds.contains(2L))
            );
        } finally {
            cleanUpVideoTable();
        }
    }

    private void prepareVideoIdsFromTemplateIds() {
        var insertVideo = """
            insert into "Video" (
                "Id",
                "GroupId",
                "CreatedTime",
                "ExternalSongIds",
                "StartListItem",
                "Country",
                "Language",
                "IsDeleted",
                "Access",
                "PublishTypeId",
                "TemplateIds"
            ) values (
                DEFAULT,
                2,
                now(),
                '{1, 2}',
                1,
                'swe',
                'eng',
                false,
                'Public',
                1,
                '{1}'
            ),
            (
                DEFAULT,
                2,
                now() - interval '1 day',
                '{3, 4}',
                null,
                'gbr',
                'eng',
                false,
                'Public',
                1,
                '{2}'
            ),
            (
                DEFAULT,
                3,
                now() - interval '2 day',
                '{5, 6}',
                null,
                'gbr',
                'eng',
                false,
                'Public',
                1,
                '{}'
            )
            """;
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertVideo);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }
}
