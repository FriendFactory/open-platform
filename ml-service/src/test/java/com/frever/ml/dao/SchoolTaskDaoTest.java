package com.frever.ml.dao;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SchoolTaskDaoTest extends DaoTestBase {
    private static final String INSERT_SCHOOL_TASK = """
        INSERT INTO "SchoolTask" (
            "Name",
            "XpPayout",
            "SoftCurrencyPayout",
            "FilesInfo",
            "CharacterCount",
            "BonusXp",
            "BonusSoftCurrency",
            "LevelId",
            "ReadinessId",
            "CreatedTime",
            "EditorSettingsId",
            "PagesNavigationId"
        )
        VALUES (
            'School Task Test',
            100,
            1000,
            '{}',
            2,
            100,
            200,
            1,
            2,
            now(),
            1,
            1
        )
        """;
    @Inject
    SchoolTaskDao schoolTaskDao;

    @Test
    public void testGetSchoolTaskLevelIds() {
        prepareOneSchoolTask();
        var result = schoolTaskDao.getSchoolTaskLevelIds();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        var first = result.getFirst();
        Assertions.assertEquals(1, first);
        cleanUp();
    }

    private void prepareOneSchoolTask() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(INSERT_SCHOOL_TASK);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUp() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"SchoolTask\"");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }
}
