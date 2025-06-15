package com.frever.ml.template.recsys;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("template-recsys")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateRecommendationResource {
    @Inject
    TemplateRecommendationService templateRecommendationService;
    @Inject
    TemplateRecommendationResponseUploadService responseUploadService;

    @Path("/{groupId}")
    @GET
    public TemplateRecommendationResponse recommendTemplate(@PathParam("groupId") long groupId) {
        var templateIds = templateRecommendationService.personalizedTemplateRecommendation(groupId);
        var result = new TemplateRecommendationResponse(templateIds);
        responseUploadService.uploadRecommendationResponseToS3(groupId, result);
        return result;
    }
}
