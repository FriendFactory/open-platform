package com.frever.platform.timers.utils;

import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ConfigUtils;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.Parameter;

public abstract class BaseSsmCredentialsProvider implements CredentialsProvider {
    abstract String getSsmUsername();

    abstract String getSsmPassword();

    @Override
    public Map<String, String> getCredentials(String credentialsProviderName) {
        if (!isOnAWS()) {
            Log.infof("Not on AWS, skipping SSM credentials provider, assuming Local");
            return Collections.emptyMap();
        }
        String ssmUsername = getSsmUsername();
        String ssmPassword = getSsmPassword();
        Log.infof(
            "On AWS, using SSM credentials provider %s, profile %s, will fetch parameters '%s' and '%s'",
            credentialsProviderName,
            ConfigUtils.getProfiles(),
            ssmUsername,
            ssmPassword
        );
        if (ssmUsername == null || ssmPassword == null) {
            return Collections.emptyMap();
        }
        if (ssmUsername.isEmpty() || ssmPassword.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> parameters = List.of(ssmUsername, ssmPassword);
        Map<String, String> credentials = new HashMap<>();
        try (var ssm = SsmClient.builder().region(Region.EU_CENTRAL_1).build()) {
            Map<String, String> credentialsInSsm =
                ssm.getParameters(builder -> builder.names(parameters).withDecryption(true))
                    .parameters()
                    .stream()
                    .collect(Collectors.toMap(Parameter::name, Parameter::value));
            credentials.put(USER_PROPERTY_NAME, credentialsInSsm.get(ssmUsername));
            credentials.put(PASSWORD_PROPERTY_NAME, credentialsInSsm.get(ssmPassword));
        } catch (Exception e) {
            Log.errorf(e, "Failed to get credentials from Parameter Store due to %s", e.getMessage());
            throw new RuntimeException(e);
        }
        return credentials;
    }

    private static boolean isOnAWS() {
        boolean runOnEc2 = Files.isDirectory(Paths.get("/home/ec2-user"));
        if (runOnEc2) {
            return true;
        }
        String ecsContainerMetadataUriV4 = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
        if (ecsContainerMetadataUriV4 == null || ecsContainerMetadataUriV4.isEmpty()) {
            return false;
        }
        try {
            var connection = (HttpURLConnection) new URI(ecsContainerMetadataUriV4).toURL().openConnection();
            connection.setConnectTimeout(200);
            connection.setReadTimeout(200);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            Log.infof(
                e,
                "Failed to contact %s due to %s, assuming local env.",
                ecsContainerMetadataUriV4,
                e.getMessage()
            );
            return false;
        }
    }
}
