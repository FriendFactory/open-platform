package com.frever.ml.comfy.messaging.sqs;

import static com.frever.ml.comfy.ComfyUiConstants.COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING;
import static com.frever.ml.comfy.ComfyUiConstants.MAX_TIMES_TO_STOP_POLLING_SQS;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.LipSync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.ComfyProperties;
import com.frever.ml.comfy.ComfyUiManager;
import com.frever.ml.comfy.messaging.ComfyUiInputAndAudioAndPromptMessage;
import com.frever.ml.comfy.messaging.ComfyUiLatentSyncMessage;
import com.frever.ml.comfy.messaging.ComfyUiVideoAndAudioAndPromptMessage;
import com.frever.ml.comfy.messaging.ComfyUiVideoLivePortraitMessage;
import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetupManager;
import com.frever.ml.messaging.DelayMessageHandlingException;
import com.frever.ml.messaging.MessageWithUnknownSubjectException;
import com.frever.ml.messaging.SnsMessage;
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import io.smallrye.context.api.ManagedExecutorConfig;
import io.smallrye.context.api.NamedInstance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.context.ManagedExecutor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

@Singleton
@Unremovable
public class ComfyUiLipSyncTaskSqsHandler extends AbstractSqsReceiver {
    @Inject
    ComfyProperties comfyProperties;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    ComfyUiManager comfyUiManager;
    @Inject
    ComfyUiTaskInstanceSetupManager taskInstanceSetupManager;
    @Inject
    @ManagedExecutorConfig(maxAsync = 9, maxQueued = 9)
    @NamedInstance("lipSyncTaskExecutor")
    ManagedExecutor taskExecutor;

    @Override
    protected String getConsumerThreadName() {
        return "ml-service-lip-sync-task-receiver";
    }

    @Override
    protected void logMessageConsumeStart() {
        Log.infof("Starting to consume task messages for LipSync instance type, queueUrl: %s", queueUrl);
    }

    @Override
    protected ManagedExecutor getTaskExecutor() {
        return taskExecutor;
    }

    @Override
    protected int getMaxNumberOfMessagesPerTurn() {
        return 2;
    }

    @Startup
    void init() {
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        queueUrl = comfyProperties.lipSyncTaskQueueUrl();
        running = true;
        consumerThread = Thread.ofPlatform().name(getConsumerThreadName()).start(this::consume);
    }

    @Shutdown
    void shutdown() {
        running = false;
        consumerThread.interrupt();
    }

    @Override
    protected boolean shouldWaitForMessage() {
        var lipSyncServerAddressAndCurrentQueueLength =
            taskInstanceSetupManager.getLipSyncServerAddressAndCurrentQueueLength();
        var server = lipSyncServerAddressAndCurrentQueueLength.serverAddress();
        var shortestQueueLength = lipSyncServerAddressAndCurrentQueueLength.currentQueueLength();
        var willWait = shortestQueueLength
            >= COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING.get(LipSync)
            * MAX_TIMES_TO_STOP_POLLING_SQS;
        if (willWait) {
            Log.infof(
                "For LipSync, shortestQueueLength is %d at %s, will wait for %d seconds before polling again.",
                shortestQueueLength,
                server,
                waitTimeSecondsBeforePolling()
            );
        }
        return willWait;
    }

    @Override
    protected void handleSqsMessage(Message message) {
        try {
            Log.infof(
                "LipSync task queue received message from Sqs, body: %s, messageId: %s",
                message.body(),
                message.messageId()
            );
            var snsMessage = objectMapper.readValue(message.body(), SnsMessage.class);
            String subject = snsMessage.subject();
            if (subject == null || subject.isBlank()) {
                Log.warnf("Received message with empty subject, payload: %s", snsMessage.payload());
                sqs.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                return;
            }
            switch (subject) {
                case "ComfyUiLatentSyncMessage" -> handleComfyUiLatentSyncMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiLatentSyncMessage.class
                ));
                case "ComfyUiLatentSyncTextMessage" -> handleComfyUiLatentSyncTextMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiVideoAndAudioAndPromptMessage.class
                ));
                case "ComfyUiSonicTextMessage" -> handleComfyUiSonicTextMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiInputAndAudioAndPromptMessage.class
                ));
                case "ComfyUiSonicAudioMessage" -> handleComfyUiSonicAudioMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiInputAndAudioAndPromptMessage.class
                ));
                case "ComfyUiVideoLivePortraitMessage" -> handleComfyUiLivePortraitMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiVideoLivePortraitMessage.class
                ));
                default -> handleDefaultMessage(snsMessage.payload());
            }
            Log.infof("Done handling message from queue %s, messageId: %s", queueUrl, message.messageId());
            sqs.deleteMessage(builder -> builder.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle()));
        } catch (JsonProcessingException e) {
            Log.warnf(e, "Failed to handle message from queue %s, messageId: %s", queueUrl, message.messageId());
            sqs.changeMessageVisibility(builder -> builder.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .visibilityTimeout(60));
        } catch (MessageWithUnknownSubjectException e) {
            sqs.changeMessageVisibility(builder -> builder.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .visibilityTimeout(600));
        } catch (DelayMessageHandlingException e) {
            sqs.changeMessageVisibility(builder -> builder.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .visibilityTimeout(e.getDelay()));
        } catch (Exception e) {
            Log.warnf(e, "Failed to handle message from Sqs, messageId: %s", message.messageId());
            sqs.changeMessageVisibility(builder -> builder.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .visibilityTimeout(600));
        }
    }

    private void handleComfyUiLatentSyncMessage(ComfyUiLatentSyncMessage comfyUiLatentSyncMessage) {
        Log.infof("Received ComfyUiLatentSyncMessage, comfyUiLatentSyncMessage: %s", comfyUiLatentSyncMessage);
        comfyUiManager.handleComfyUiLatentSyncMessage(comfyUiLatentSyncMessage);
    }

    private void handleComfyUiLatentSyncTextMessage(ComfyUiVideoAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiLatentSyncTextMessage, comfyUiLatentSyncTextMessage: %s", message);
        comfyUiManager.handleComfyUiLatentSyncTextMessage(message);
    }

    private void handleComfyUiSonicAudioMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiSonicAudioMessage, comfyUiSonicMessage: %s", message);
        comfyUiManager.handleComfyUiSonicAudioMessage(message);
    }

    private void handleComfyUiSonicTextMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiSonicTextMessage, comfyUiSonicMessage: %s", message);
        comfyUiManager.handleComfyUiSonicTextMessage(message);
    }

    private void handleComfyUiLivePortraitMessage(ComfyUiVideoLivePortraitMessage message) {
        Log.infof("Received ComfyUiLivePortraitMessage, comfyUiLivePortraitMessage: %s", message);
        comfyUiManager.handleComfyUiLivePortraitMessage(message);
    }

}
