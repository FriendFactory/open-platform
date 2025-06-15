package com.frever.ml.comfy.messaging.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.ComfyProperties;
import com.frever.ml.comfy.ComfyUiManager;
import com.frever.ml.comfy.dto.ComfyUiWorkflow;
import com.frever.ml.comfy.dto.OngoingPromptHandoverMessage;
import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup;
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
import java.util.Map;
import org.eclipse.microprofile.context.ManagedExecutor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

@Singleton
@Unremovable
public class SqsReceiverForComfyUi extends AbstractSqsReceiver {
    @Inject
    ComfyProperties comfyProperties;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    ComfyUiManager comfyUiManager;
    @Inject
    @ManagedExecutorConfig(maxAsync = 2, maxQueued = 2)
    @NamedInstance("taskExecutor")
    ManagedExecutor taskExecutor;

    Map<ComfyUiTaskInstanceSetup, String> comfyUiTaskInstanceSetupToTaskQueueUrl;

    @Override
    protected String getConsumerThreadName() {
        return "ml-service-sqs-receiver";
    }

    @Override
    protected void logMessageConsumeStart() {
        Log.infof("Starting to consume messages, queueUrl: %s", queueUrl);
    }

    @Override
    protected ManagedExecutor getTaskExecutor() {
        return taskExecutor;
    }

    @Startup
    void init() {
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        queueUrl = comfyProperties.queueUrl();
        running = true;
        consumerThread = Thread.ofPlatform().name(getConsumerThreadName()).start(this::consume);
        comfyUiTaskInstanceSetupToTaskQueueUrl = Map.of(
            ComfyUiTaskInstanceSetup.LipSync, comfyProperties.lipSyncTaskQueueUrl(),
            ComfyUiTaskInstanceSetup.Pulid, comfyProperties.pulidTaskQueueUrl(),
            ComfyUiTaskInstanceSetup.Makeup, comfyProperties.makeupTaskQueueUrl()
        );
    }

    @Shutdown
    void shutdown() {
        running = false;
        consumerThread.interrupt();
    }

    @Override
    protected void handleSqsMessage(Message message) {
        try {
            Log.infof(
                "Task receiver queue received message from Sqs, body: %s, messageId: %s",
                message.body(),
                message.messageId()
            );
            var snsMessage = objectMapper.readValue(message.body(), SnsMessage.class);
            String subject = snsMessage.subject();
            if (subject == null || subject.isBlank()) {
                Log.warnf("Received message with empty subject, payload: %s", snsMessage.payload());
                return;
            }
            if ("OngoingPromptHandoverMessage".equals(subject)) {
                handleOngoingPromptHandoverMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    OngoingPromptHandoverMessage.class
                ));
                Log.infof("Done handling message from Sqs, messageId: %s", message.messageId());
                sqs.deleteMessage(builder -> builder.queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle()));
                return;
            }
            var setup = ComfyUiWorkflow.fromSqsSubjectToComfyUiTaskInstanceSetup(subject);
            if (setup == null) {
                Log.warnf("Received message with unknown subject, payload: %s", snsMessage.payload());
                return;
            }
            var taskQueueUrl = comfyUiTaskInstanceSetupToTaskQueueUrl.get(setup);
            if (taskQueueUrl == null || taskQueueUrl.isBlank()) {
                Log.warnf("TaskQueueUrl is null, setup: %s", setup);
                return;
            }
            Log.infof(
                "TaskQueueUrl: %s, message subject %s, payload %s",
                taskQueueUrl,
                snsMessage.subject(),
                snsMessage.payload()
            );
            sqs.sendMessage(builder -> {
                builder.queueUrl(taskQueueUrl);
                builder.messageBody(message.body());
                builder.messageAttributes(message.messageAttributes());
            });
            Log.infof("Done handling message from receiver queue, messageId: %s", message.messageId());
            sqs.deleteMessage(builder -> builder.queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle()));
        } catch (JsonProcessingException e) {
            Log.warnf(e, "Failed to handle message from Sqs, messageId: %s", message.messageId());
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

    private void handleOngoingPromptHandoverMessage(OngoingPromptHandoverMessage ongoingPromptHandoverMessage) {
        Log.infof(
            "Received OngoingPromptHandoverMessage, OngoingPromptHandoverMessage: %s",
            ongoingPromptHandoverMessage
        );
        comfyUiManager.handleOngoingPromptHandoverMessage(ongoingPromptHandoverMessage);
    }
}
