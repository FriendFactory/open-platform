package com.frever.ml.dao;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CrewDaoTest extends DaoTestBase {
    private static final String INSERT_CREW_MEMBER = """
        INSERT INTO "CrewMember" (
            "CrewId",
            "GroupId",
            "CrewRoleId",
            "RemovedAt"
        )
        VALUES (
            1,
            1,
            1,
            NULL
        ),
        (
            1,
            2,
            2,
            NULL
        ),
        (
            1,
            3,
            3,
            NULL
        )
        """;

    @Inject
    CrewDao crewDao;

    @Test
    public void testGetCrewMembers() {
        prepareData();
        var result = crewDao.getCrewMembers(1);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertAll(
            () -> Assertions.assertTrue(result.contains(2L)),
            () -> Assertions.assertTrue(result.contains(3L))
        );
        cleanData();
    }

    private void prepareData() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(INSERT_CREW_MEMBER);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanData() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"CrewMember\"");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }
}
