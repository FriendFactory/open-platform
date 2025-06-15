package com.frever.cmsAuthorization.utils;

import io.quarkus.arc.Unremovable;
import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.logging.Log;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.Parameter;

@ApplicationScoped
@Named("ssm-credentials-provider")
@Unremovable
public class SsmCredentialsProvider implements CredentialsProvider {
    private static final String EC2_METADATA_SERVICE_URL = "http://169.254.169.254";

    @ConfigProperty(name = "ssm.datasource.username", defaultValue = "/cms-authorization/db-username")
    String ssmUsername;

    @ConfigProperty(name = "ssm.datasource.password", defaultValue = "/cms-authorization/db-password")
    String ssmPassword;

    @Override
    public Map<String, String> getCredentials(String credentialsProviderName) {
        if (!isOnAWS()) {
            Log.infof("Not on AWS, skipping SSM credentials provider, assuming Local");
            return Collections.emptyMap();
        }
        Log.infof("On AWS, using SSM credentials provider %s", credentialsProviderName);
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
        try {
            var connection = (HttpURLConnection) new URL(EC2_METADATA_SERVICE_URL).openConnection();
            connection.setConnectTimeout(200);
            connection.setReadTimeout(200);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            Log.infof("Failed to contact %s due to %s, assuming local env.", EC2_METADATA_SERVICE_URL, e.getMessage());
            return false;
        }
    }
}
