package com.frever.ml.comfy.comm;

import static com.frever.ml.utils.Utils.DEFAULT_WAITING_TIME_SECONDS;
import static com.frever.ml.utils.Utils.isProd;
import static com.frever.ml.utils.Utils.isDev;
import static com.frever.ml.utils.Utils.normalizeServerIp;

import com.frever.ml.comfy.ComfyProperties;
import com.frever.ml.comfy.ComfyUiManager;
import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetupManager;
import com.frever.ml.dao.ComfyUiInstanceStatusReportDao;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Singleton
public class ReportQueueInfoToSlack {
    public static final String SLACK_WEBHOOK_URL =
        // ai-transformation-info
        "https://hooks.slack.com/services/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";
    // slack-integration-test
    // "https://hooks.slack.com/services/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";

    private static final int REPORT_INTERVAL_MIN = 2;
    private static final DateTimeFormatter PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String COMFY_UI_STATUS_SLACK_MESSAGE_TEMPLATE = """
        {
            "text": "Env: %s, time: %s UTC. Queue status: ComfyUi has %d tasks remaining, task duration estimation: %d seconds. "
        }
        """;

    private static final String SQS_STATUS_SLACK_MESSAGE_TEMPLATE = """
        {
            "text": "Env: %s, time: %s UTC. SQS status: Sqs has %d tasks remaining. "
        }
        """;

    private HttpClient httpClient;
    private String currentEnv;
    @Inject
    ComfyUiManager comfyUiManager;
    @Inject
    ComfyUiTaskInstanceSetupManager comfyUiTaskInstanceSetupManager;
    @Inject
    ComfyUiInstanceStatusReportDao comfyUiInstanceStatusReportDao;
    @Inject
    ComfyProperties comfyProperties;

    @PostConstruct
    void postConstruct() {
        httpClient = HttpClient.newHttpClient();
        if (isDev()) {
            currentEnv = "dev";
        } else if (isProd()) {
            currentEnv = "prod";
        } else {
            currentEnv = "unknown";
        }
    }

    @Scheduled(every = "5m", delayed = "1m")
    public void reportQueueInfo() {
        // var serverIp = comfyUiVideoManager.getComfyUiServerIpAndPort();
        var serverIp = comfyProperties.comfyUiLipSyncInstanceAddress();
        if (!comfyUiInstanceStatusReportDao.comfyUiInstanceExists(serverIp)) {
            comfyUiInstanceStatusReportDao.addComfyUiInstance(serverIp);
        }
        if (comfyUiInstanceStatusReportDao.reportedWithinMinutes(serverIp, REPORT_INTERVAL_MIN)) {
            Log.infof("Reported within the last %s minutes, will not report again.", REPORT_INTERVAL_MIN);
            return;
        }
        int sqsQueueRemaining = comfyUiTaskInstanceSetupManager.sqsRemaining(comfyProperties.queueUrl());
        if (sqsQueueRemaining > 0) {
            postMessageToSlack(String.format(
                SQS_STATUS_SLACK_MESSAGE_TEMPLATE,
                currentEnv,
                LocalDateTime.now().format(PATTERN),
                sqsQueueRemaining
            ));
        }
        if (sqsQueueRemaining == -1) {
            Log.info("Most probably running locally, do not report...");
            return;
        }
        var locked = comfyUiInstanceStatusReportDao.beginReport(serverIp);
        if (!locked) {
            Log.info("beginReport returned false, there is already a report in progress.");
            return;
        }
        try {
            int comfyUiQueueRemaining =
                comfyUiTaskInstanceSetupManager.comfyUiQueueRemaining(comfyProperties.comfyUiLipSyncInstanceAddress());
            if (comfyUiQueueRemaining < 0) {
                Log.info("Video service is not running, do not report...");
                return;
            }
            if (comfyUiQueueRemaining == 0
                && comfyUiInstanceStatusReportDao.zeroReported(serverIp)) {
                Log.info("ComfyUi queue is empty, already reported.");
                return;
            }
            int taskDurationEstimation;
            if (comfyUiQueueRemaining == 0) {
                taskDurationEstimation = DEFAULT_WAITING_TIME_SECONDS;
            } else {
                taskDurationEstimation =
                    comfyUiManager.comfyUiQueueTimeEstimationSeconds(normalizeServerIp(serverIp));
            }
            postMessageToSlack(String.format(
                COMFY_UI_STATUS_SLACK_MESSAGE_TEMPLATE,
                currentEnv,
                LocalDateTime.now().format(PATTERN),
                comfyUiQueueRemaining,
                taskDurationEstimation
            ));
            if (comfyUiQueueRemaining == 0) {
                comfyUiInstanceStatusReportDao.markZeroReported(serverIp);
            } else {
                comfyUiInstanceStatusReportDao.markReport(serverIp);
            }
        } finally {
            comfyUiInstanceStatusReportDao.endReport(serverIp);
        }
    }

    private void postMessageToSlack(String message) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SLACK_WEBHOOK_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(message))
            .build();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Log.warnf(e, "Failed to send message to Slack.");
        }
    }
}
