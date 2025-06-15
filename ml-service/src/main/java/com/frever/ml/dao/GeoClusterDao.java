package com.frever.ml.dao;

import com.frever.ml.dto.GeoCluster;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class GeoClusterDao extends BaseDao {
    private static final String SELECT_GEO_CLUSTER = """
        SELECT "IncludeVideoFromCountry" as includeVideoFromCountry,
            "IncludeVideoWithLanguage" as includeVideoWithLanguage,
            "Priority" as priority,
            "RecommendationVideosPool" as numberOfVideos,
            "RecommendationNumOfDaysLookback" as numberOfDays,
            "ShowToUserFromCountry" as showToUserFromCountry,
            "ShowForUserWithLanguage" as showForUserWithLanguage
        FROM "GeoCluster"
            WHERE "IsActive" = true
            ORDER BY "Priority" DESC
        """;

    public List<GeoCluster> getGeoClusters() {
        return jdbi.withHandle(handle -> handle.createQuery(SELECT_GEO_CLUSTER).mapTo(GeoCluster.class).list());
    }
}
