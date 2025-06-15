package com.frever.ml.dao;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class UserDaoTest extends DaoTestBase {
    @Inject
    UserDao userDao;

    private static final String BLOCKED_USERS = """
        INSERT INTO "BlockedUser" (
            "BlockedUserId",
            "BlockedByUserId"
        )
        VALUES (
            1,
            2
        ),
        (
            3,
            1
        )
        """;

    @Test
    public void testGetUserInfo() {
        prepareUserInfo();
        var result = userDao.getUserInfo(1);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.groupId());
        Assertions.assertEquals(1, result.userId());
        cleanUpUserInfo();
    }

    @Test
    public void testGetBlockedUsers() {
        prepareBlockedUsers();
        var result = userDao.getBlockedUsers(1);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertAll(
            () -> Assertions.assertTrue(result.contains(2L)),
            () -> Assertions.assertTrue(result.contains(3L))
        );
        cleanUpBlockedUsers();
    }

    private void prepareBlockedUsers() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(BLOCKED_USERS);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUpBlockedUsers() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"BlockedUser\"");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void prepareUserInfo() {
        var insertUserInfo = """
            INSERT INTO "Group" (
                "Id",
                "Name",
                "TaxationCountryId",
                "DefaultLanguageId"
            ) VALUES (
                DEFAULT,
                'Test Group',
                1,
                1
            );
            """;
        var insertCountry = """
            INSERT INTO "Country" (
                "Id",
                "ISOName"
            ) VALUES (
                DEFAULT,
                'US'
            );
            """;
        var insertLanguage = """
            INSERT INTO "Language" (
                "Id",
                "IsoCode"
            ) VALUES (
                DEFAULT,
                'en'
            );
            """;
        var insertUserAndGroup = """
            INSERT INTO "UserAndGroup" (
                "UserId",
                "GroupId"
            ) VALUES (
                1,
                1
            );
            """;
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(insertUserInfo);
            statement.execute(insertCountry);
            statement.execute(insertLanguage);
            statement.execute(insertUserAndGroup);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUpUserInfo() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate \"Group\" restart identity");
            statement.execute("DELETE FROM \"Country\"");
            statement.execute("DELETE FROM \"Language\"");
            statement.execute("DELETE FROM \"UserAndGroup\"");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGetFollowInfo() {
        prepareFollowInfo();
        var result = userDao.getFollowInfo(1);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.get(2L));
        Assertions.assertFalse(result.get(3L));
        cleanUpFollowInfo();
    }

    private void cleanUpFollowInfo() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"Follower\"");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void prepareFollowInfo() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(
                "INSERT INTO \"Follower\" (\"FollowerId\", \"FollowingId\", \"IsMutual\") VALUES (1, 2, true)");
            statement.execute(
                "INSERT INTO \"Follower\" (\"FollowerId\", \"FollowingId\", \"IsMutual\") VALUES (1, 3, false)");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testUserExtraInfoSetup() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(
                "insert into stats.user_extra_info (group_id, last_login) values (1, now() - INTERVAL '31 day')");
            statement.execute(
                "insert into stats.user_extra_info (group_id, last_login) values (2, now() - INTERVAL '32 day')");
            statement.execute(
                "insert into stats.user_extra_info (group_id, last_login) values (3, now() - INTERVAL '3 day')");
        } catch (Exception e) {
            Assertions.fail(e);
        }
        List<Long> deleted = jdbi.withHandle(handle -> handle.createQuery(
                "DELETE FROM stats.user_extra_info WHERE last_login < now() - INTERVAL '1 month' returning group_id")
            .mapTo(Long.class)
            .list());
        Assertions.assertEquals(2, deleted.size());
        Assertions.assertAll(
            () -> Assertions.assertTrue(deleted.contains(1L)),
            () -> Assertions.assertTrue(deleted.contains(2L))
        );
    }
}
