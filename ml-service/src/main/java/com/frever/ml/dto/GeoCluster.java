package com.frever.ml.dto;

import java.util.List;

public record GeoCluster(List<String> includeVideoFromCountry, List<String> includeVideoWithLanguage, int priority,
                         long numberOfVideos, int numberOfDays, List<String> showToUserFromCountry,
                         List<String> showForUserWithLanguage) {
    public static final GeoCluster GLOBAL = new GeoCluster(
        List.of("*"),
        List.of("*"),
        1,
        3000,
        10,
        List.of("*"),
        List.of("*")
    );

    public boolean allCountryAllowed() {
        return includeVideoFromCountry.contains("*");
    }

    public boolean allLanguageAllowed() {
        return includeVideoWithLanguage.contains("*");
    }
}
