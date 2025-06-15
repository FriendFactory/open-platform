package com.frever.ml.comfy.messaging.sqs;

import static com.frever.ml.comfy.ComfyUiConstants.SQS_POLL_BACKOFF_WHEN_CONNECTION_ISSUE;
import static com.frever.ml.utils.Utils.invalidQueueUrl;
import static com.frever.ml.utils.Utils.isDev;
import static com.frever.ml.utils.Utils.isProd;

import com.frever.ml.messaging.MessageWithUnknownSubjectException;
import io.quarkus.logging.Log;
import java.net.SocketException;
import org.eclipse.microprofile.context.ManagedExecutor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

public abstract class AbstractSqsReceiver {
    public static final int WAIT_TIME_SECONDS = 20;
    protected SqsClient sqs;
    protected Thread consumerThread;
    protected String queueUrl;
    protected volatile boolean running = false;

    protected abstract String getConsumerThreadName();

    protected abstract void logMessageConsumeStart();

    protected abstract ManagedExecutor getTaskExecutor();

    protected abstract void handleSqsMessage(Message message);

    protected int getMaxNumberOfMessagesPerTurn() {
        return 10;
    }

    protected boolean shouldWaitForMessage() {
        return false;
    }

    protected int waitTimeSecondsBeforePolling() {
        return WAIT_TIME_SECONDS;
    }

    protected void consume() {
        logMessageConsumeStart();
        if (invalidQueueUrl(queueUrl)) {
            Log.error("QueueUrl is null or blank or not Sqs, will not consume messages.");
            return;
        }
        if (!isDev() && !isProd()) {
            Log.warnf("QueueUrl is %s, but not in dev or prod environment, will not consume messages.", queueUrl);
            return;
        }
        while (running && queueUrl != null && !queueUrl.isBlank()) {
            if (shouldWaitForMessage()) {
                try {
                    Thread.sleep(waitTimeSecondsBeforePolling());
                    continue;
                } catch (InterruptedException e) {
                    Log.warnf(e, "Failed to sleep before polling Sqs %s", queueUrl);
                    // Thread.currentThread().interrupt();
                    continue;
                }
            }
            try {
                sqs.receiveMessage(builder -> builder.queueUrl(queueUrl)
                        .waitTimeSeconds(WAIT_TIME_SECONDS)
                        .maxNumberOfMessages(getMaxNumberOfMessagesPerTurn()))
                    .messages()
                    .forEach(message -> {
                        var taskExecutor = getTaskExecutor();
                        if (taskExecutor != null) {
                            taskExecutor.submit(() -> handleSqsMessage(message));
                        } else {
                            handleSqsMessage(message);
                        }
                    });
            } catch (Exception e) {
                Log.warnf(e, "Failed to consume messages from Sqs %s", queueUrl);
                if (e.getCause() instanceof SocketException) {
                    Log.infof("Backoff when cannot reach ComfyUi.");
                    try {
                        Thread.sleep(SQS_POLL_BACKOFF_WHEN_CONNECTION_ISSUE);
                    } catch (InterruptedException ie) {
                        Log.warnf(ie, "Failed to back when cannot reach ComfyUi.");
                        // Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    protected void handleDefaultMessage(String payload) {
        Log.warnf("Received message with unknown subject, payload: %s", payload);
        throw new MessageWithUnknownSubjectException();
    }
}
