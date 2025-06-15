package com.frever.ml.follow.recsys;

import com.frever.ml.dto.EgoNetwork;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("follow-recommendation")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FollowRecommendationResource {
    @Inject
    FollowRecommendationService followRecommendationService;
    @Inject
    FollowRecommendationResponseUploadService responseUploadService;

    @GET
    @Path("/follow/{groupId}")
    public FollowRecommendation getRecommendations(@PathParam("groupId") long groupId) {
        Log.infof("Generate follow recommendation for GroupId %s", groupId);
        FollowRecommendation followRecommendation = followRecommendationService.getFollowRecommendation(groupId);
        responseUploadService.uploadRecommendationResponseToS3(groupId, followRecommendation);
        return followRecommendation;
    }

    @GET
    @Path("/cold-start/{groupId}")
    public ColdStart getColdStart(@PathParam("groupId") long groupId) {
        return followRecommendationService.getColdStart(groupId);
    }

    @GET
    @Path("/common-friends/{groupId}")
    public CommonFriends getSecondHopFriends(@PathParam("groupId") long groupId) {
        return new CommonFriends(followRecommendationService.getSecondHopFriends(groupId));
    }

    @GET
    @Path("/follow-back/{groupId}")
    public FollowBack getFollowBack(@PathParam("groupId") long groupId) {
        Log.infof("Generate follow-back for GroupId %s", groupId);
        List<Long> followBack = followRecommendationService.getFollowBack(groupId);
        FollowBack followback = new FollowBack(followBack.stream()
            .map(f -> new FollowBack.FollowBackItem(f, "followback", List.of()))
            .toList());
        responseUploadService.uploadFollowBackResponseToS3(groupId, followback);
        return followback;
    }

    @GET
    @Path("/ego-network/{groupId}")
    public EgoNetwork getEgoNetwork(@PathParam("groupId") long groupId) {
        return followRecommendationService.getEgoNetwork(groupId);
    }

    @Path("/follow-action")
    @POST
    public Response getFollowAction() {
        Log.info("Received POST request: follow-action");
        return Response.ok().entity("{\"status\": \"OK\"}").build();
    }
}
