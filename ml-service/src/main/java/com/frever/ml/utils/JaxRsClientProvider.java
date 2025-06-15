package com.frever.ml.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.concurrent.TimeUnit;
import org.jboss.resteasy.plugins.providers.multipart.MultipartEntityPartWriter;

@ApplicationScoped
public class JaxRsClientProvider {
    @Singleton
    @Produces
    public Client client() {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS);
        Client client = clientBuilder.build();
        client.register(MultipartEntityPartWriter.class);
        return client;
    }
}
