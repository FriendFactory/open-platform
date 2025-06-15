package com.frever.ml.apis;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/status")
@ApplicationScoped
public class StatusResource {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String status() {
        return "Hello from ml-service";
    }
}
