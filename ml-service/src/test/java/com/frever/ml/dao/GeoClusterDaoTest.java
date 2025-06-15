package com.frever.ml.dao;

import com.frever.ml.dto.GeoCluster;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class GeoClusterDaoTest extends DaoTestBase {
    private static final String INSERT_GEO_CLUSTER = """
        INSERT INTO "GeoCluster" (
            "Priority",
            "Title",
            "IsActive",
            "IncludeVideoFromCountry",
            "ExcludeVideoFromCountry",
            "IncludeVideoWithLanguage",
            "ExcludeVideoWithLanguage",
            "ShowToUserFromCountry",
            "HideForUserFromCountry",
            "ShowForUserWithLanguage",
            "HideForUserWithLanguage",
            "RecommendationVideosPool",
            "RecommendationNumOfDaysLookback"
        )
        VALUES (
            634,
            'Sweden-language all',
            true,
            '{swe}',
            '{}',
            '{*}',
            '{}',
            '{swe}',
            '{}',
            '{*}',
            '{}',
            3000,
            10
        )
        """;
    @Inject
    GeoClusterDao geoClusterDao;

    @Test
    public void testGetGeoClusters() {
        prepareOneGeoCluster();
        var result = geoClusterDao.getGeoClusters();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        GeoCluster first = result.getFirst();
        Assertions.assertEquals(634, first.priority());
        Assertions.assertEquals(3000, first.numberOfVideos());
        cleanUp();
    }

    private void prepareOneGeoCluster() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(INSERT_GEO_CLUSTER);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    private void cleanUp() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("truncate \"GeoCluster\" restart identity");
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }
}
