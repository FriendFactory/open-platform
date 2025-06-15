package com.frever.ml.feed.recsys;

import static com.frever.ml.dto.GeoCluster.GLOBAL;

import com.frever.ml.dao.GeoClusterDao;
import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.UserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class GeoClusterService {
    @Inject
    GeoClusterDao geoClusterDao;

    volatile List<GeoCluster> geoClusters = Collections.emptyList();
    volatile Instant lastUpdated = Instant.MIN;

    public List<GeoCluster> getGeoClusters() {
        if (geoClusters.isEmpty() || lastUpdated.isBefore(Instant.now().minus(10, ChronoUnit.MINUTES))) {
            geoClusters = geoClusterDao.getGeoClusters();
            lastUpdated = Instant.now();
        }
        return geoClusters;
    }

    public List<GeoCluster> assignedGeoClusters(UserInfo userInfo) {
        if (userInfo == null) {
            return List.of(GLOBAL);
        }
        var geoClusters = getGeoClusters();
        var assignedGeoClusters = new ArrayList<GeoCluster>();
        for (var geoCluster : geoClusters) {
            boolean countryAllowed = isCountryAllowed(userInfo, geoCluster);
            boolean languageAllowed = isLanguageAllowed(userInfo, geoCluster);
            if (countryAllowed && languageAllowed) {
                assignedGeoClusters.add(geoCluster);
            }
        }
        if (assignedGeoClusters.isEmpty()) {
            assignedGeoClusters.add(GLOBAL);
        }
        return assignedGeoClusters;
    }

    private static boolean isLanguageAllowed(UserInfo userInfo, GeoCluster geoCluster) {
        return geoCluster.showForUserWithLanguage().contains(userInfo.language())
            || geoCluster.showForUserWithLanguage().contains("*");
    }

    private static boolean isCountryAllowed(UserInfo userInfo, GeoCluster geoCluster) {
        return geoCluster.showToUserFromCountry().contains(userInfo.country())
            || geoCluster.showToUserFromCountry().contains("*");
    }
}
