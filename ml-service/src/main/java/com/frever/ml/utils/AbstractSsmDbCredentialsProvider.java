package com.frever.ml.utils;

import static com.frever.ml.utils.Utils.isOnAWS;

import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ConfigUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.Parameter;

public abstract class AbstractSsmDbCredentialsProvider implements CredentialsProvider {
    abstract protected String getSsmUsername();

    abstract protected String getSsmPassword();

    @Override
    public Map<String, String> getCredentials(String credentialsProviderName) {
        if (!isOnAWS()) {
            Log.infof(
                "Not on AWS, skipping SSM credentials provider, assuming Local, profiles %s",
                ConfigUtils.getProfiles()
            );
            return Collections.emptyMap();
        }
        Log.infof(
            "On AWS, using SSM credentials provider %s, profile %s, will fetch parameters '%s' and '%s'",
            credentialsProviderName,
            ConfigUtils.getProfiles(),
            getSsmUsername(),
            getSsmPassword()
        );
        List<String> parameters = List.of(getSsmUsername(), getSsmPassword());
        Map<String, String> credentials = new HashMap<>();
        try (var ssm = SsmClient.builder().region(Region.EU_CENTRAL_1).build()) {
            Map<String, String> credentialsInSsm =
                ssm.getParameters(builder -> builder.names(parameters).withDecryption(true))
                    .parameters()
                    .stream()
                    .collect(Collectors.toMap(Parameter::name, Parameter::value));
            credentials.put(USER_PROPERTY_NAME, credentialsInSsm.get(getSsmUsername()));
            credentials.put(PASSWORD_PROPERTY_NAME, credentialsInSsm.get(getSsmPassword()));
        } catch (Exception e) {
            Log.errorf(e, "Failed to get credentials from Parameter Store due to %s", e.getMessage());
            throw new RuntimeException(e);
        }
        return credentials;
    }
}
