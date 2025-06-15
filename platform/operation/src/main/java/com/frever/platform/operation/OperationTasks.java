package com.frever.platform.operation;

import static com.frever.platform.operation.DbUtils.*;
import static com.frever.platform.operation.S3Utils.LIFECYCLE_EXPIRATION_DELETE_MARKER_CREATED;
import static com.frever.platform.operation.S3Utils.OBJECT_REMOVED_DELETE;
import static com.frever.platform.operation.S3Utils.OBJECT_REMOVED_DELETE_MARKER_CREATED;
import static com.frever.platform.operation.S3Utils.getS3Client;
import static com.frever.platform.operation.S3Utils.handleDefault;
import static com.frever.platform.operation.S3Utils.handleDeleteFromObjectRemoved;
import static com.frever.platform.operation.S3Utils.handleDeleteMarkerCreatedFromLifecycle;
import static com.frever.platform.operation.S3Utils.handleDeleteMarkerCreatedFromObjectRemoved;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.Twilio;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

@ApplicationScoped
public class OperationTasks {

    @Inject
    ObjectMapper objectMapper;

    @Scheduled(every = "1m")
    void checkLongRunningQueries(ScheduledExecution execution) {
        Log.infof(
            "Will check long running queries at %s, which is scheduled at %s",
            execution.getFireTime(),
            execution.getScheduledFireTime()
        );
        try (var snsClient = getSnsClient()) {
            var connections = getDBConnections();
            connections.forEach((dbName, connection) -> {
                try (var statement = connection.createStatement()) {
                    var resultSet = statement.executeQuery(CHECK_LONG_RUNNING_QUERY);
                    while (resultSet.next()) {
                        var query = resultSet.getString("query");
                        if (shouldReportQuery(query)) {
                            var emailMessage =
                                "This Query: " + query + " has been running for more than 70 seconds against db "
                                    + dbName;
                            var slackMessage = SLACK_MESSAGE_TEMPLATE.replace(
                                    "%subject%",
                                    "Query Running Longer Than 70 Seconds"
                                )
                                .replace("%reason%", "Query Running Longer Than 70 Seconds")
                                .replace("%now%", LocalDateTime.now().toString())
                                .replace("%statistic%", query)
                                .replace("%db-name%", dbName)
                                .replace("%period%", "60")
                                .replace("%threshold%", "60 seconds");
                            publishAlerts(snsClient, emailMessage, slackMessage);
                        }
                    }
                } catch (SQLException e) {
                    Log.error("Error while checking long running queries for db: {}", dbName, e);
                }
            });
        }
    }

    private static boolean shouldReportQuery(String query) {
        return !query.startsWith("START_REPLICATION") && !query.startsWith("VACUUM") && !query.startsWith("autovacuum");
    }

    @Scheduled(every = "2m")
    void checkReplication(ScheduledExecution execution) {
        Log.infof(
            "Will check replication status at %s, which is scheduled at %s",
            execution.getFireTime(),
            execution.getScheduledFireTime()
        );
        try (var snsClient = getSnsClient()) {
            var connections = getDBConnections();
            connections.forEach((dbName, connection) -> {
                try (var statement = connection.createStatement()) {
                    var resultSet =
                        statement.executeQuery("SELECT slot_name FROM pg_replication_slots WHERE NOT active;");
                    while (resultSet.next()) {
                        var slotName = resultSet.getString("slot_name");
                        var message =
                            "This slot: " + slotName + " is not active, please check replication for db " + dbName;
                        publishAlertsPlatform(snsClient, message);
                    }
                } catch (SQLException e) {
                    Log.error("Error while checking replication for db: {}", dbName, e);
                }
            });
        }
    }

    @Scheduled(every = "5m")
    void checkLongOpeningTransaction(ScheduledExecution execution) {
        Log.infof(
            "Will check long opening transactions at %s, which is scheduled at %s",
            execution.getFireTime(),
            execution.getScheduledFireTime()
        );
        try (var snsClient = getSnsClient()) {
            var connections = getDBConnections();
            connections.forEach((dbName, connection) -> {
                try (var statement = connection.createStatement()) {
                    var resultSet = statement.executeQuery(CHECK_LONG_OPEN_TRANSACTIONS);
                    while (resultSet.next()) {
                        var query = resultSet.getString("query");
                        if (!query.startsWith("START_REPLICATION")) {
                            var emailMessage =
                                "There is transaction opening longer than 10 minutes. Query: " + query + ", against db "
                                    + dbName;
                            var slackMessage = SLACK_MESSAGE_TEMPLATE.replace(
                                    "%subject%",
                                    "Transaction opening Longer Than 10 minutes"
                                )
                                .replace("%reason%", "Transaction opening Longer Than 10 minutes")
                                .replace("%now%", LocalDateTime.now().toString())
                                .replace("%statistic%", query)
                                .replace("%db-name%", dbName)
                                .replace("%period%", "300")
                                .replace("%threshold%", "10 minutes");
                            publishAlerts(snsClient, emailMessage, slackMessage);
                        }
                    }
                } catch (SQLException e) {
                    Log.error("Error while checking long opening transactions for db: " + dbName, e);
                }
            });
        }
    }

    @Scheduled(every = "2h")
    void shutdownRDSesTaggedAutoShutdown(ScheduledExecution execution) {
        Log.infof(
            "Will shut down RDSes tagged with auto-shutdown at %s, which is scheduled at %s",
            execution.getFireTime(),
            execution.getScheduledFireTime()
        );
        try (var rdsClient = getRdsClient()) {
            var dbs = rdsClient.describeDBInstances();
            dbs.dbInstances()
                .stream()
                .filter(db -> !db.dbInstanceIdentifier().toLowerCase().startsWith("prod") && db.dbInstanceStatus()
                    .equals("available") && db.tagList() != null && db.tagList().contains(AUTO_STOP_TAG))
                .forEach(db -> {
                    Log.infof("Will stop RDS instance: %s", db.dbInstanceIdentifier());
                    rdsClient.stopDBInstance(builder -> builder.dbInstanceIdentifier(db.dbInstanceIdentifier()));
                });
        }
    }

    @Scheduled(every = "1h")
    void pollPlatformOperationInputQueue(ScheduledExecution execution) {
        Log.infof(
            "Will poll platform-operation-input-queue at %s, which is scheduled at %s",
            execution.getFireTime(),
            execution.getScheduledFireTime()
        );
        try (var sqsClient = getSqsClient(); var s3Client = getS3Client()) {
            while (true) {
                var messages = sqsClient.receiveMessage(builder -> {
                    builder.queueUrl(PLATFORM_OPERATION_INPUT_QUEUE_URL);
                    builder.maxNumberOfMessages(10);
                    builder.waitTimeSeconds(20);
                }).messages();
                if (messages.isEmpty()) {
                    break;
                }
                messages.forEach(message -> {
                    Log.infof("Will process message with ID %s", message.messageId());
                    AtomicBoolean deleteMessage = new AtomicBoolean();
                    try {
                        S3Event event = objectMapper.readValue(message.body(), S3Event.class);
                        List<S3Event.Record> records = event.records;
                        if (Objects.isNull(records)) {
                            Log.errorf("No records found in the message %s : %s", message.messageId(), message.body());
                            deleteMessage.set(true);
                            return;
                        }
                        if (records.size() != 1) {
                            Log.warnf(
                                "Received %d records from message %s : %s",
                                records.size(),
                                message.messageId(),
                                message.body()
                            );
                            return;
                        }
                        records.forEach(record -> {
                            record.originalMessage = message;
                            switch (record.eventName) {
                                case LIFECYCLE_EXPIRATION_DELETE_MARKER_CREATED ->
                                    handleDeleteMarkerCreatedFromLifecycle(s3Client, record, deleteMessage);
                                case OBJECT_REMOVED_DELETE_MARKER_CREATED -> handleDeleteMarkerCreatedFromObjectRemoved(
                                    sqsClient,
                                    s3Client,
                                    record,
                                    deleteMessage
                                );
                                case OBJECT_REMOVED_DELETE ->
                                    handleDeleteFromObjectRemoved(s3Client, record, deleteMessage);
                                default -> {
                                    handleDefault(record, deleteMessage);
                                }
                            }
                        });
                    } catch (JsonProcessingException e) {
                        Log.errorf(e, "Error while parsing message with ID %s", message.messageId());
                    } finally {
                        if (deleteMessage.get()) {
                            Log.infof("Will delete message with ID %s", message.messageId());
                            sqsClient.deleteMessage(builder -> {
                                builder.queueUrl(PLATFORM_OPERATION_INPUT_QUEUE_URL);
                                builder.receiptHandle(message.receiptHandle());
                            });
                        }
                    }
                });
            }
        }
        Log.infof(
            "Finished polling platform-operation-input-queue at %s, which was fired at %s",
            Instant.now(),
            execution.getFireTime()
        );
    }

    @Scheduled(every = "8h")
    void deleteOldTwilioHistoryLog(ScheduledExecution execution) {
        Log.infof(
            "Will delete old Twilio history logs at %s, which is scheduled at %s",
            execution.getFireTime(),
            execution.getScheduledFireTime()
        );
        try (var ssmClient = getSsmClient()) {
            var parameters = ssmClient.getParameters(GetParametersRequest.builder()
                .names("/content-prod/secrets/twilio-sid", "/content-prod/secrets/twilio-secret")
                .withDecryption(true)
                .build());
            var twilioCredential = parameters.parameters()
                .stream()
                .collect(toMap(
                    parameter -> parameter.name().substring(parameter.name().lastIndexOf("/") + 1),
                    Parameter::value
                ));
            Twilio.init(twilioCredential.get("twilio-sid"), twilioCredential.get("twilio-secret"));
            TwilioDeleteHistory.deleteOldCallLogs();
            TwilioDeleteHistory.deleteOldSmsLogs();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Log.infof(
            "Finished delete old Twilio history logs at %s, which was fired at %s",
            Instant.now(),
            execution.getFireTime()
        );
    }

    @Scheduled(every = "8h")
    void removeStarCreatorCandidateOlderThanOneMonth(ScheduledExecution execution) {
        Log.infof(
            "Will remove star-creator-candidate older than one month at %s, which is scheduled at %s",
            execution.getFireTime(),
            execution.getScheduledFireTime()
        );
        try (var connection = getMainDbConnection(); var statement = connection.createStatement()) {
            var deleteSql = """
                delete from "StarCreatorCandidate" where "CreatedTime" < now() - INTERVAL '30 days';
                """;
            statement.execute(deleteSql);
        } catch (SQLException e) {
            Log.error("Error remove old StarCreatorCandidate for main db.", e);
        }
    }
}
