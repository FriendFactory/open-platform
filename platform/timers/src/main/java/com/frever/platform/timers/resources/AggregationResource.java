package com.frever.platform.timers.resources;

import com.frever.platform.timers.followerStats.FollowEdgeAggregationService;
import com.frever.platform.timers.followerStats.FollowerStatsAggregationService;
import com.frever.platform.timers.followerStats.UserExtraInfoService;
import com.frever.platform.timers.template.TemplateAggregationService;
import com.frever.platform.timers.template.TemplateRankingService;
import com.frever.platform.timers.videoKpi.VideoKpiAggregationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/aggregation")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AggregationResource {
    @Inject
    TemplateAggregationService templateAggregationService;
    @Inject
    FollowerStatsAggregationService followerStatsAggregationService;
    @Inject
    VideoKpiAggregationService videoKpiAggregationService;
    @Inject
    FollowEdgeAggregationService followEdgeAggregationService;
    @Inject
    TemplateRankingService templateRankingService;
    @Inject
    UserExtraInfoService userExtraInfoService;

    @POST
    @Path("/bootstrap/template")
    public void bootstrapTemplate() {
        templateAggregationService.bootstrap();
    }

    @POST
    @Path("/bootstrap/follower")
    public void bootstrapFollowerStats() {
        followerStatsAggregationService.bootstrap();
    }

    @POST
    @Path("/bootstrap/follow-edge")
    public void bootstrapFollowEdge() {
        followEdgeAggregationService.bootstrap();
    }

    @POST
    @Path("/bootstrap/user/last-login")
    public void bootstrapUserLastLogin() {
        userExtraInfoService.bootstrapUserLastLogin();
    }

    @POST
    @Path("/bootstrap/video-kpi")
    public void bootstrapVideoKpi() {
        videoKpiAggregationService.bootstrap();
    }

    @POST
    @Path("/aggregate/template/{templateId}")
    public void aggregate(@PathParam("templateId") long templateId) {
        templateAggregationService.aggregateTemplate(templateId);
    }

    @POST
    @Path("/bootstrap/template/{templateId}")
    public void bootstrapOneTemplate(@PathParam("templateId") long templateId) {
        templateAggregationService.bootstrapOneTemplate(templateId);
    }

    @POST
    @Path("/bootstrap/template/songs")
    public void bootstrapTemplateSongIds() {
        templateAggregationService.bootstrapTemplateSongIds();
    }

    @POST
    @Path("/bootstrap/video-kpi/remix")
    public void recalculateVideoKpiRemix() {
        videoKpiAggregationService.recalculateVideoKpiRemix();
    }

    @POST
    @Path("/bootstrap/template-ranking")
    public void bootstrapTemplateRanking() {
        templateRankingService.initTemplateRankingTable();
    }

    @POST
    @Path("/bootstrap/video-kpi/like-combined")
    public void initVideoLikesCombined() {
        videoKpiAggregationService.initVideoLikesCombined();
    }
}
