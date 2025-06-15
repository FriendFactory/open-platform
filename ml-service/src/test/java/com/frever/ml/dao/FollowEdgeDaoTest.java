package com.frever.ml.dao;

import com.frever.ml.follow.recsys.SecondHopFriends;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FollowEdgeDaoTest extends DaoTestBase {
    @Inject
    FollowEdgeDao followEdgeDao;

    private static final String INSERT_FOLLOW_EDGE = """
        insert into stats.follow_edge (source, destination,
            is_mutual, source_is_minor, source_strict_coppa_rules, destination_is_minor, destination_strict_coppa_rules)
        values
            (1, 2, true, false, false, false, false),
            (1, 3, false, false, false, false, false),
            (2, 1, true, false, false, false, false),
            (3, 2, true, false, false, false, false),
            (1, 4, false, false, false, false, false),
            (2, 3, true, false, false, false, false),
            (5, 2, false, false, false, false, false),
            (5, 1, false, false, false, false, false),
            (4, 2, false, true, true, false, false),
            (4, 3, false, false, false, true, true)
        """;

    private static final String INSERT_USER_EXTRA_INFO = """
        insert into stats.user_extra_info (group_id, last_login)
        values
            (1, now()),
            (2, now()),
            (3, now()),
            (4, now()),
            (5, now())
        """;

    private static final String INSERT_INFLUENTIAL_NODES = """
        insert into stats.influential_nodes (destination, rank)
        values (2, 1), (3, 2), (1, 3), (4, 4), (5, 5)
        """;

    @BeforeEach
    public void init() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(INSERT_FOLLOW_EDGE);
            statement.execute(INSERT_INFLUENTIAL_NODES);
            statement.execute(INSERT_USER_EXTRA_INFO);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @AfterEach
    public void cleanup() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("delete from stats.follow_edge");
            statement.execute("delete from stats.influential_nodes");
            statement.execute("delete from stats.user_extra_info");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGetMostInfluentialNodes() {
        List<Long> mostInfluentialNodes = followEdgeDao.getMostInfluentialNodes(1L);
        Assertions.assertAll(
            () -> Assertions.assertEquals(2, mostInfluentialNodes.size()),
            () -> Assertions.assertEquals(2L, (long) mostInfluentialNodes.getFirst()),
            () -> Assertions.assertEquals(3L, (long) mostInfluentialNodes.getLast())
        );
    }

    @Test
    public void testGetMostInfluentialNodesOptimized() {
        List<Long> mostInfluentialNodes = followEdgeDao.getMostInfluentialNodesOptimized(1L);
        System.out.println("mostInfluentialNodes: " + mostInfluentialNodes);
        Assertions.assertAll(
            () -> Assertions.assertEquals(1, mostInfluentialNodes.size()),
            () -> Assertions.assertEquals(5L, (long) mostInfluentialNodes.getFirst())
        );
    }

    @Test
    public void testGetFollowBack() {
        List<Long> followBack = followEdgeDao.getFollowBack(1L);
        Assertions.assertAll(
            () -> Assertions.assertEquals(1, followBack.size()),
            () -> Assertions.assertEquals(5L, followBack.getFirst())
        );
    }

    @Test
    public void testGetFirstHopFriends() {
        Map<Long, Boolean> firstHopFriends = followEdgeDao.getFirstHopFriends(1L);
        Assertions.assertAll(
            () -> Assertions.assertEquals(3, firstHopFriends.size()),
            () -> Assertions.assertTrue(firstHopFriends.get(2L)),
            () -> Assertions.assertFalse(firstHopFriends.get(3L)),
            () -> Assertions.assertFalse(firstHopFriends.get(4L))
        );
    }

    @Test
    public void testGetSecondHopFriendsWithLimit() {
        List<SecondHopFriends> secondHopFriendsWithLimit = followEdgeDao.getSecondHopFriendsWithLimit(1L);
        Assertions.assertTrue(secondHopFriendsWithLimit.isEmpty());
    }

    @Test
    public void testGetAllSecondHopFriends() {
        List<Long> allSecondHopFriends = followEdgeDao.getAllSecondHopFriends(1L);
        Assertions.assertTrue(allSecondHopFriends.isEmpty());
    }
}
