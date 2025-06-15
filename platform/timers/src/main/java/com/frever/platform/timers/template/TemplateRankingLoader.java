package com.frever.platform.timers.template;

import static com.frever.platform.timers.utils.RedshiftQueries.GET_TEMPLATE_RANKINGS;
import static com.frever.platform.timers.utils.Utils.inFreverProd;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.redshiftdata.RedshiftDataAsyncClient;
import software.amazon.awssdk.services.redshiftdata.model.DescribeStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.Field;
import software.amazon.awssdk.services.redshiftdata.model.GetStatementResultRequest;
import software.amazon.awssdk.services.redshiftdata.model.RedshiftDataException;

@Singleton
public class TemplateRankingLoader {
    private static final String REDSHIFT_CLUSTER_IDENTIFIER = "redshift-analytics";
    private static final String REDSHIFT_DATABASE = "frever";
    private static final String REDSHIFT_DB_USER = "kafkaprod";

    private RedshiftDataAsyncClient redshiftDataAsyncClient;

    @PostConstruct
    public void init() {
        if (!inFreverProd()) {
            Log.info("Not in frever-prod, skipping TemplateRankingLoader initialization.");
            return;
        }
        redshiftDataAsyncClient = createAsyncDataClient();
    }

    public List<Long> loadTemplateRankings() {
        if (!inFreverProd()) {
            return Collections.emptyList();
        }
        ExecuteStatementRequest statementRequest = ExecuteStatementRequest.builder()
            .clusterIdentifier(REDSHIFT_CLUSTER_IDENTIFIER)
            .database(REDSHIFT_DATABASE)
            .dbUser(REDSHIFT_DB_USER)
            .sql(GET_TEMPLATE_RANKINGS)
            .build();

        var statementId = CompletableFuture.supplyAsync(() -> {
            try {
                ExecuteStatementResponse response =
                    redshiftDataAsyncClient.executeStatement(statementRequest).join();
                return response.id();
            } catch (RedshiftDataException e) {
                throw new RuntimeException("Error executing statement: " + e.getMessage(), e);
            }
        }).exceptionally(exception -> {
            Log.warn("Got exception when loading template ranking from Redshift.", exception);
            return "";
        }).join();

        Log.infof("Statement ID: %s", statementId);

        if (statementId == null || statementId.isEmpty()) {
            Log.warn("Statement ID is null or empty.");
            return Collections.emptyList();
        }

        try {
            checkStatementAsync(statementId).join();
        } catch (Exception e) {
            Log.warn("Error checking statement.", e);
            return Collections.emptyList();
        }

        GetStatementResultRequest resultRequest = GetStatementResultRequest.builder()
            .id(statementId)
            .build();

        CompletableFuture<List<Long>> handle = redshiftDataAsyncClient.getStatementResult(resultRequest)
            .handle((response, exception) -> {
                if (exception != null) {
                    Log.info("Error getting statement result.", exception);
                    throw new RuntimeException("Error getting statement result: " + exception.getMessage(), exception);
                }

                Log.infof("Response total number: %s", response.totalNumRows());

                if (!response.hasRecords()) {
                    Log.warn("No records in response.");
                    return Collections.emptyList();
                }
                return response.records().stream()
                    .map(List::getFirst)
                    .map(Field::longValue)
                    .collect(Collectors.toList());
            });
        return handle.join();
    }

    RedshiftDataAsyncClient createAsyncDataClient() {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
            .maxConcurrency(100)
            .connectionTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(180))
            .writeTimeout(Duration.ofSeconds(180))
            .build();

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofMinutes(4))
            .apiCallAttemptTimeout(Duration.ofSeconds(180))
            .retryStrategy(RetryMode.STANDARD)
            .build();

        return RedshiftDataAsyncClient.builder()
            .httpClient(httpClient)
            .overrideConfiguration(overrideConfig)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    private CompletableFuture<Void> checkStatementAsync(String sqlId) {
        DescribeStatementRequest statementRequest = DescribeStatementRequest.builder()
            .id(sqlId)
            .build();

        return redshiftDataAsyncClient.describeStatement(statementRequest)
            .thenCompose(response -> {
                String status = response.statusAsString();
                Log.infof("Query Status: %s", status);

                if ("FAILED".equals(status)) {
                    throw new RuntimeException("The Query Failed.");
                } else if ("FINISHED".equals(status)) {
                    return CompletableFuture.completedFuture(null);
                } else {
                    return CompletableFuture.runAsync(() -> {
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Error during sleep: " + e.getMessage(), e);
                        }
                    }).thenCompose(ignore -> checkStatementAsync(sqlId));
                }
            }).whenComplete((result, exception) -> {
                if (exception != null) {
                    Log.info("Error when querying Redshift.", exception);
                }
            });
    }
}
