package com.frever.ml.comfy.messaging.sqs;

import static com.frever.ml.comfy.ComfyUiConstants.COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING;
import static com.frever.ml.comfy.ComfyUiConstants.MAX_TIMES_TO_STOP_POLLING_SQS;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.Pulid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.ComfyProperties;
import com.frever.ml.comfy.ComfyUiManager;
import com.frever.ml.comfy.messaging.ComfyUiMultiPhotosAndPromptMessage;
import com.frever.ml.comfy.messaging.ComfyUiPhotoAndPromptMessage;
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
public class ComfyUiPulidTaskSqsHandler extends AbstractSqsReceiver {
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
    @NamedInstance("pulidTaskExecutor")
    ManagedExecutor taskExecutor;

    @Override
    protected String getConsumerThreadName() {
        return "ml-service-pulid-task-receiver";
    }

    @Override
    protected void logMessageConsumeStart() {
        Log.infof("Starting to consume task messages for Pulid instance type, queueUrl: %s", queueUrl);
    }

    @Override
    protected ManagedExecutor getTaskExecutor() {
        return taskExecutor;
    }

    @Startup
    void init() {
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        queueUrl = comfyProperties.pulidTaskQueueUrl();
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
        var pulidServerAddressAndCurrentQueueLength =
            taskInstanceSetupManager.getPulidServerAddressAndCurrentQueueLength();
        var server = pulidServerAddressAndCurrentQueueLength.serverAddress();
        var shortestQueueLength = pulidServerAddressAndCurrentQueueLength.currentQueueLength();
        var willWait = shortestQueueLength
            >= COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING.get(Pulid)
            * MAX_TIMES_TO_STOP_POLLING_SQS;
        if (willWait) {
            Log.infof(
                "For Pulid, shortestQueueLength is %d at %s, will wait for %d seconds before polling again.",
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
                "Pulid task queue received message from Sqs, body: %s, messageId: %s",
                message.body(),
                message.messageId()
            );
            var snsMessage = objectMapper.readValue(message.body(), SnsMessage.class);
            String subject = snsMessage.subject();
            if (subject == null || subject.isBlank()) {
                Log.warnf("Received message with empty subject, payload: %s", snsMessage.payload());
                return;
            }
            switch (subject) {
                case "ComfyUiPhotoPulidMultiCharMessage" ->
                    handleComfyUiPhotoPulidMultiCharMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiMultiPhotosAndPromptMessage.class
                    ));
                case "ComfyUiFluxPhotoPromptMessage" -> handleComfyUiFluxPhotoPromptMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiPhotoAndPromptMessage.class
                ));
                case "ComfyUiFluxPromptMessage" -> handleComfyUiFluxPromptMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiPhotoAndPromptMessage.class
                ));
                case "ComfyUiFluxPhotoReduxStyleMessage" ->
                    handleComfyUiFluxPhotoReduxStyleMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiMultiPhotosAndPromptMessage.class
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

    private void handleComfyUiFluxPhotoPromptMessage(ComfyUiPhotoAndPromptMessage comfyUiPhotoAndPromptMessage) {
        Log.infof(
            "Received ComfyUiFluxPhotoPromptMessage, comfyUiPhotoAndPromptMessage: %s",
            comfyUiPhotoAndPromptMessage
        );
        comfyUiManager.handleComfyUiFluxPhotoPromptMessage(comfyUiPhotoAndPromptMessage);
    }

    private void handleComfyUiFluxPromptMessage(ComfyUiPhotoAndPromptMessage comfyUiPhotoAndPromptMessage) {
        Log.infof("Received ComfyUiFluxPromptMessage, comfyUiPhotoAndPromptMessage: %s", comfyUiPhotoAndPromptMessage);
        comfyUiManager.handleComfyUiFluxPromptMessage(comfyUiPhotoAndPromptMessage);
    }

    private void handleComfyUiFluxPhotoReduxStyleMessage(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        Log.infof(
            "Received ComfyUiFluxPhotoReduxStyleMessage, comfyUiTwoPhotosAndPromptMessage: %s",
            comfyUiMultiPhotosAndPromptMessage
        );
        comfyUiManager.handleComfyUiFluxPhotoReduxStyleMessage(comfyUiMultiPhotosAndPromptMessage);
    }

    private void handleComfyUiPhotoPulidMultiCharMessage(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        Log.infof(
            "Received ComfyUiPhotoPulidMultiChar, comfyUiPhotoPulidMultiChar: %s",
            comfyUiMultiPhotosAndPromptMessage
        );
        comfyUiManager.handleComfyUiPhotoPulidMultiCharMessage(comfyUiMultiPhotosAndPromptMessage);
    }
}
