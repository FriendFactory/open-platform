package com.frever.ml.utils;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named("ssm-credentials-provider")
@Unremovable
public class SsmDbCredentialsProvider extends AbstractSsmDbCredentialsProvider {
    @ConfigProperty(name = "ssm.datasource.username", defaultValue = "/ixia-prod-postgresql-rds/username")
    String ssmUsername;

    @ConfigProperty(name = "ssm.datasource.password", defaultValue = "/ixia-prod-postgresql-rds/password")
    String ssmPassword;

    @Override
    protected String getSsmUsername() {
        return ssmUsername;
    }

    @Override
    protected String getSsmPassword() {
        return ssmPassword;
    }

}
