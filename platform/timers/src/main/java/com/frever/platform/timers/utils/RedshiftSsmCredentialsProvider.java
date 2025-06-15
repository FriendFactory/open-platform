package com.frever.platform.timers.utils;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named("ssm-credentials-provider-redshift")
@Unremovable
public class RedshiftSsmCredentialsProvider extends BaseSsmCredentialsProvider {

    @ConfigProperty(name = "ssm.datasource.redshift.username")
    String ssmUsername;

    @ConfigProperty(name = "ssm.datasource.redshift.password")
    String ssmPassword;

    @Override
    String getSsmUsername() {
        return ssmUsername;
    }

    @Override
    String getSsmPassword() {
        return ssmPassword;
    }
}
