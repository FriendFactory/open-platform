package com.frever.ml.utils;

import com.google.common.collect.ImmutableMap;
import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import java.util.Optional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@QuarkusTestResource(PostgresqlDbTestResource.class)
public class PostgresqlDbTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {
    public static final String USER_PROPERTY_NAME = "quarkus.datasource.username";
    public static final String PASSWORD_PROPERTY_NAME = "quarkus.datasource.password";
    public static final String JDBC_URL_PROPERTY_NAME = "quarkus.datasource.jdbc.url";
    public static final String ML_USER_PROPERTY_NAME = "quarkus.datasource.ml.username";
    public static final String ML_PASSWORD_PROPERTY_NAME = "quarkus.datasource.ml.password";
    public static final String ML_JDBC_URL_PROPERTY_NAME = "quarkus.datasource.ml.jdbc.url";
    private static Optional<String> CONTAINER_NETWORK_ID;
    private static PostgreSQLContainer CONTAINER;

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        CONTAINER_NETWORK_ID = context.containerNetworkId();
    }

    @Override
    public Map<String, String> start() {
        // start a container making sure to call withNetworkMode() with the value of containerNetworkId if present
        var myImage = DockerImageName.parse("postgis/postgis:14-3.4-alpine").asCompatibleSubstituteFor("postgres");
        CONTAINER = new PostgreSQLContainer<>(myImage)
            .withDatabaseName("main")
            .withUsername("main")
            .withPassword("test")
            .withLogConsumer(outputFrame -> {
            });

        // apply the network to the container
        CONTAINER_NETWORK_ID.ifPresent(CONTAINER::withNetworkMode);

        // start container before retrieving its URL or other properties
        CONTAINER.start();

        String jdbcUrl = CONTAINER.getJdbcUrl();
        if (CONTAINER_NETWORK_ID.isPresent()) {
            // Replace hostname + port in the provided JDBC URL with the hostname of the Docker container
            // running PostgreSQL and the listening port.
            jdbcUrl = fixJdbcUrl(jdbcUrl);
        }

        // return a map containing the configuration the application needs to use the service
        return ImmutableMap.of(
            USER_PROPERTY_NAME, CONTAINER.getUsername(),
            PASSWORD_PROPERTY_NAME, CONTAINER.getPassword(),
            JDBC_URL_PROPERTY_NAME, jdbcUrl,
            ML_USER_PROPERTY_NAME, CONTAINER.getUsername(),
            ML_PASSWORD_PROPERTY_NAME, CONTAINER.getPassword(),
            ML_JDBC_URL_PROPERTY_NAME, jdbcUrl
        );
    }

    private String fixJdbcUrl(String jdbcUrl) {
        // Part of the JDBC URL to replace
        String hostPort = CONTAINER.getHost() + ':' + CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);

        // Host/IP on the container network plus the unmapped port
        String networkHostPort =
            CONTAINER.getCurrentContainerInfo().getConfig().getHostName()
                + ':'
                + PostgreSQLContainer.POSTGRESQL_PORT;

        return jdbcUrl.replace(hostPort, networkHostPort);
    }

    @Override
    public void stop() {
        // close container
        CONTAINER.stop();
    }
}
