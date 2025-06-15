package com.frever.ml.utils;

import io.quarkus.liquibase.LiquibaseFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSetStatus;
import liquibase.exception.LiquibaseException;

@ApplicationScoped
public class DbMigrationService {
    @Inject
    LiquibaseFactory liquibaseFactory;

    public void checkMigration() throws LiquibaseException {
        Log.info("Checking Liquibase migration status");
        try (Liquibase liquibase = liquibaseFactory.createLiquibase()) {
            List<ChangeSetStatus> status =
                liquibase.getChangeSetStatuses(liquibaseFactory.createContexts(), liquibaseFactory.createLabels());
            Log.infof("Liquibase status: %s", status);
        }
    }
}
