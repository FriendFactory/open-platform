package com.frever.platform.operation;

import static java.util.stream.Collectors.toMap;

import io.quarkus.logging.Log;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.Tag;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;

public final class DbUtils {
    static final String EMAIL_SNS_ARN = "arn:aws:sns:eu-central-1:722913253728:system-alerts";
    static final String SLACK_SNS_ARN =
        "arn:aws:sns:eu-central-1:722913253728:FreverMonitoringStack-Alarms04B5A0BF-WC0J6XJ59IFB";

    static final String PLATFORM_SNS_ARN = "arn:aws:sns:eu-central-1:722913253728:platform-messages";

    static final String USER_NAME = "Username";
    static final String HOST = "Host";
    static final String PASSWORD = "Password";
    static final String DB_NAME = "Database";
    static final int DB_PORT = 5432;
    static final String CHECK_LONG_RUNNING_QUERY = """
        SELECT query FROM pg_stat_activity
         WHERE pg_stat_activity.state='active' and
               EXTRACT(epoch from (now() - pg_stat_activity.query_start)) > 70;
        """;

    static final String CHECK_LONG_OPEN_TRANSACTIONS = """
        SELECT query FROM pg_stat_activity
         WHERE state in ('active', 'idle in transaction', 'idle in transaction (aborted)') and
               EXTRACT(epoch from (now() - query_start)) > 600;
        """;

    static final String SLACK_MESSAGE_TEMPLATE = """
        {
            "NewStateReason": "%reason%",
            "StateChangeTime": "%now%",
            "Trigger": {
              "MetricName": "ExecutionTime",
              "Namespace": "AWS/RDS",
              "Statistic": "%statistic%",
              "ComparisonOperator": "GreaterThanThreshold",
              "Dimensions": [
                {
                  "name": "DBInstanceIdentifier",
                  "value": "%db-name%"
                }
              ],
              "Period": %period%,
              "Threshold": "%threshold%"
            }
        }
        """;

    static final Tag AUTO_STOP_TAG = Tag.builder().key("AutoStop").value("true").build();

    static final String PLATFORM_OPERATION_INPUT_QUEUE_URL = "https://sqs.eu-central-1.amazonaws.com/722913253728/platform-operation-input-queue-eu-central-1";

    static void publishAlerts(SnsClient snsClient, String emailMessage, String slackMessage) {
        Log.infof("Publishing email alert: %s", emailMessage);
        Log.infof("Publishing slack alert: %s", slackMessage);
        var forEmail = PublishRequest.builder().topicArn(EMAIL_SNS_ARN).message(emailMessage).build();
        var forSlack = PublishRequest.builder().topicArn(SLACK_SNS_ARN).message(slackMessage).build();
        snsClient.publish(forEmail);
        snsClient.publish(forSlack);
    }

    static void publishAlertsPlatform(SnsClient snsClient, String message) {
        Log.infof("Publishing alert: %s", message);
        var messageToPublish = PublishRequest.builder().topicArn(PLATFORM_SNS_ARN).message(message).build();
        snsClient.publish(messageToPublish);
    }

    static SnsClient getSnsClient() {
        return SnsClient.builder().httpClient(ApacheHttpClient.create()).region(Region.EU_CENTRAL_1).build();
    }

    static SsmClient getSsmClient() {
        return SsmClient.builder().httpClient(ApacheHttpClient.create()).region(Region.EU_CENTRAL_1).build();
    }

    static RdsClient getRdsClient() {
        return RdsClient.builder().httpClient(ApacheHttpClient.create()).region(Region.EU_CENTRAL_1).build();
    }

    static SqsClient getSqsClient() {
        return SqsClient.builder().httpClient(ApacheHttpClient.create()).region(Region.EU_CENTRAL_1).build();
    }

    static Map<String, String> getDBConnectionParameters(String connectionUrl) {
        var result = new HashMap<String, String>();
        var keyValues = connectionUrl.split(";");
        for (var keyValue : keyValues) {
            var keyAndValue = keyValue.split("=");
            result.put(keyAndValue[0], keyAndValue[1]);
        }
        return result;
    }

    static Map<String, Connection> getDBConnections() {
        try (var ssmClient = getSsmClient()) {
            var parameters = ssmClient.getParameters(GetParametersRequest.builder()
                .names("/content-prod/secrets/cs-main", "/content-prod/secrets/cs-auth")
                .withDecryption(true)
                .build());
            return parameters.parameters()
                .stream()
                .collect(toMap(
                    parameter -> parameter.name().substring(parameter.name().lastIndexOf("/") + 1),
                    parameter -> {
                        try {
                            var connectionUrl = parameter.value();
                            return getConnectionFromConnectionUrl(connectionUrl);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                ));
        }
    }

    static Connection getVideoDbConnection() {
        return getDbConnection("cs-main");
    }

    static Connection getMainDbConnection() {
        return getDbConnection("cs-main");
    }

    private static Connection getDbConnection(String parameterName) {
        try (var ssmClient = getSsmClient()) {
            var parameters = ssmClient.getParameter(GetParameterRequest.builder()
                .name("/content-prod/secrets/" + parameterName)
                .withDecryption(true)
                .build());
            var connectionUrl = parameters.parameter().value();
            return getConnectionFromConnectionUrl(connectionUrl);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Connection getConnectionFromConnectionUrl(String connectionUrl) throws SQLException {
        var connectionParameters = getDBConnectionParameters(connectionUrl);
        var userName = connectionParameters.get(USER_NAME);
        var password = connectionParameters.get(PASSWORD);
        var url = "jdbc:postgresql://" + connectionParameters.get(HOST) + ":"
            + DB_PORT + "/" + connectionParameters.get(DB_NAME);
        return DriverManager.getConnection(url, userName, password);
    }

}
