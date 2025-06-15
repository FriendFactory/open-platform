package com.frever.platform.timers.utils;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named("ssm-credentials-provider-main")
@Unremovable
public class MainDbSsmCredentialsProvider extends BaseSsmCredentialsProvider {

    @ConfigProperty(name = "ssm.datasource.main.username")
    String ssmUsername;

    @ConfigProperty(name = "ssm.datasource.main.password")
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
