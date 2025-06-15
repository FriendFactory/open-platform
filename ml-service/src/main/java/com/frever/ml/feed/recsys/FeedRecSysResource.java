package com.frever.ml.feed.recsys;

import com.frever.ml.dto.GeoLocation;
import com.frever.ml.dto.RecommendedVideoResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Path("feed-recsys")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class FeedRecSysResource {
    @Inject
    FeedRecSysService feedRecSysService;
    @Inject
    FeedRecommendationResponseUploadService responseUploadService;

    @GET
    @Path("/recommend")
    public List<RecommendedVideoResponse> recommend(
        @QueryParam("groupId") long groupId,
        @QueryParam("lon") double lon,
        @QueryParam("lat") double lat,
        @Context HttpHeaders headers
    ) {
        var userAgent = headers.getHeaderString("User-Agent");
        var freverExperimentHeader = headers.getHeaderString("X-Frever-Experiments");
        var freverExperiment = extractHeaderValues(freverExperimentHeader);
        Log.info("Received request for recommendations for groupId: " + groupId + " with user agent: " + userAgent
            + " and frever experiment: " + freverExperiment + ", lon: " + lon + ", lat: " + lat);
        List<RecommendedVideoResponse> recommendations =
            feedRecSysService.getRecommendations(groupId, freverExperiment, new GeoLocation(lon, lat));
        if (!recommendations.isEmpty()) {
            responseUploadService.uploadResponseToS3(groupId, recommendations);
        } else {
            Log.warn("No recommendations found for groupId: " + groupId);
        }
        return recommendations;
    }

    private static HashMap<String, String> extractHeaderValues(String freverExperimentHeader) {
        var freverExperiment = new HashMap<String, String>();
        if (freverExperimentHeader != null && !freverExperimentHeader.isEmpty()) {
            Arrays.stream(freverExperimentHeader.split(",")).forEach(experiment -> {
                var parts = experiment.split("=");
                freverExperiment.put(parts[0], parts[1]);
            });
        }
        return freverExperiment;
    }
}
