package com.frever.cmsAuthorization.utils;

import com.google.common.collect.ImmutableMap;
import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import java.util.Optional;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@QuarkusTestResource(PostgresqlDbTestResource.class)
public class PostgresqlDbTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {

    public static final String USER_PROPERTY_NAME = "quarkus.datasource.username";
    public static final String PASSWORD_PROPERTY_NAME = "quarkus.datasource.password";
    public static final String JDBC_URL_PROPERTY_NAME = "quarkus.datasource.jdbc.url";
    private Optional<String> containerNetworkId;
    private JdbcDatabaseContainer container;

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        containerNetworkId = context.containerNetworkId();
    }

    @Override
    public Map<String, String> start() {
        // start a container making sure to call withNetworkMode() with the value of containerNetworkId if present
        container = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("cms")
            .withUsername("cms")
            .withPassword("test")
            .withLogConsumer(outputFrame -> {
            });

        // apply the network to the container
        containerNetworkId.ifPresent(container::withNetworkMode);

        // start container before retrieving its URL or other properties
        container.start();

        String jdbcUrl = container.getJdbcUrl();
        if (containerNetworkId.isPresent()) {
            // Replace hostname + port in the provided JDBC URL with the hostname of the Docker container
            // running PostgreSQL and the listening port.
            jdbcUrl = fixJdbcUrl(jdbcUrl);
        }

        // return a map containing the configuration the application needs to use the service
        return ImmutableMap.of(
            USER_PROPERTY_NAME, container.getUsername(),
            PASSWORD_PROPERTY_NAME, container.getPassword(),
            JDBC_URL_PROPERTY_NAME, jdbcUrl
        );
    }

    private String fixJdbcUrl(String jdbcUrl) {
        // Part of the JDBC URL to replace
        String hostPort = container.getHost() + ':' + container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);

        // Host/IP on the container network plus the unmapped port
        String networkHostPort =
            container.getCurrentContainerInfo().getConfig().getHostName()
                + ':'
                + PostgreSQLContainer.POSTGRESQL_PORT;

        return jdbcUrl.replace(hostPort, networkHostPort);
    }

    @Override
    public void stop() {
        // close container
        container.stop();
    }
}
