package com.frever.ml.comfy.messaging.sqs;

import static com.frever.ml.comfy.ComfyUiConstants.COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING;
import static com.frever.ml.comfy.ComfyUiConstants.MAX_TIMES_TO_STOP_POLLING_SQS;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.Makeup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.ComfyProperties;
import com.frever.ml.comfy.ComfyUiManager;
import com.frever.ml.comfy.messaging.ComfyUiInputAndAudioAndPromptMessage;
import com.frever.ml.comfy.messaging.ComfyUiMultiPhotosAndPromptMessage;
import com.frever.ml.comfy.messaging.ComfyUiMusicGenMessage;
import com.frever.ml.comfy.messaging.ComfyUiPhotoAcePlusMessage;
import com.frever.ml.comfy.messaging.ComfyUiVideoAndAudioAndPromptMessage;
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
public class ComfyUiMakeupTaskSqsHandler extends AbstractSqsReceiver {
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
    @NamedInstance("makeupTaskExecutor")
    ManagedExecutor taskExecutor;

    @Override
    protected String getConsumerThreadName() {
        return "ml-service-makeup-task-receiver";
    }

    @Override
    protected void logMessageConsumeStart() {
        Log.infof("Starting to consume task messages for Makeup instance type, queueUrl: %s", queueUrl);
    }

    @Override
    protected ManagedExecutor getTaskExecutor() {
        return taskExecutor;
    }

    @Startup
    void init() {
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        queueUrl = comfyProperties.makeupTaskQueueUrl();
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
        var makeupServerAddressAndCurrentQueueLength =
            taskInstanceSetupManager.getMakeupServerAddressAndCurrentQueueLength();
        var server = makeupServerAddressAndCurrentQueueLength.serverAddress();
        var shortestQueueLength = makeupServerAddressAndCurrentQueueLength.currentQueueLength();
        var willWait = shortestQueueLength
            >= COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING.get(Makeup)
            * MAX_TIMES_TO_STOP_POLLING_SQS;
        if (willWait) {
            Log.infof(
                "For Makeup, shortestQueueLength is %d at %s, will wait for %d seconds before polling again.",
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
                "Makeup task queue received message from Sqs, body: %s, messageId: %s",
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
                case "ComfyUiPhotoMakeUpThumbnailsMessage" ->
                    handleComfyUiPhotoMakeUpThumbnailsMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiMultiPhotosAndPromptMessage.class
                    ));
                case "ComfyUiPhotoMakeUpEyebrowsThumbnailsMessage" ->
                    handleComfyUiPhotoMakeUpEyebrowsThumbnailsMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiMultiPhotosAndPromptMessage.class
                    ));
                case "ComfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage" ->
                    handleComfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiMultiPhotosAndPromptMessage.class
                    ));
                case "ComfyUiPhotoMakeUpLipsThumbnailsMessage" ->
                    handleComfyUiPhotoMakeUpLipsThumbnailsMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiMultiPhotosAndPromptMessage.class
                    ));
                case "ComfyUiPhotoMakeUpSkinThumbnailsMessage" ->
                    handleComfyUiPhotoMakeUpSkinThumbnailsMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiMultiPhotosAndPromptMessage.class
                    ));
                case "ComfyUiPhotoAcePlusMessage" -> handleComfyUiPhotoAcePlusMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiPhotoAcePlusMessage.class
                ));
                case "ComfyUiVideoOnOutputTextMessage" -> handleComfyUiVideoOnOutputTextMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiVideoAndAudioAndPromptMessage.class
                ));
                case "ComfyUiVideoOnOutputAudioMessage" ->
                    handleComfyUiVideoOnOutputAudioMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        ComfyUiVideoAndAudioAndPromptMessage.class
                    ));
                case "ComfyUiStillImageAudioMessage" -> handleComfyUiStillImageAudioMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiInputAndAudioAndPromptMessage.class
                ));
                case "ComfyUiStillImageTextMessage" -> handleComfyUiStillImageTextMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiInputAndAudioAndPromptMessage.class
                ));
                case "ComfyUiMusicGenMessage" -> handleComfyUiMusicGenMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiMusicGenMessage.class
                ));
                case "ComfyUiMmAudioMessage" -> handleComfyUiMmAudioMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    ComfyUiInputAndAudioAndPromptMessage.class
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

    private void handleComfyUiPhotoMakeUpThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        Log.infof(
            "Received ComfyUiPhotoMakeUpThumbnailsMessage, comfyUiPhotoMakeUpThumbnailsMessage: %s",
            comfyUiMultiPhotosAndPromptMessage
        );
        comfyUiManager.handleComfyUiPhotoMakeUpThumbnailsMessage(comfyUiMultiPhotosAndPromptMessage);
    }

    private void handleComfyUiPhotoMakeUpSkinThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        Log.infof(
            "Received ComfyUiPhotoMakeUpSkinThumbnailsMessage, comfyUiPhotoMakeUpSkinThumbnailsMessage: %s",
            comfyUiMultiPhotosAndPromptMessage
        );
        comfyUiManager.handleComfyUiPhotoMakeUpSkinThumbnailsMessage(comfyUiMultiPhotosAndPromptMessage);
    }

    private void handleComfyUiPhotoMakeUpLipsThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        Log.infof(
            "Received ComfyUiPhotoMakeUpLipsThumbnailsMessage, comfyUiPhotoMakeUpLipsThumbnailsMessage: %s",
            comfyUiMultiPhotosAndPromptMessage
        );
        comfyUiManager.handleComfyUiPhotoMakeUpLipsThumbnailsMessage(comfyUiMultiPhotosAndPromptMessage);
    }

    private void handleComfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        Log.infof(
            "Received ComfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage, "
                + "comfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage: %s",
            comfyUiMultiPhotosAndPromptMessage
        );
        comfyUiManager.handleComfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage(comfyUiMultiPhotosAndPromptMessage);
    }

    private void handleComfyUiPhotoMakeUpEyebrowsThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        Log.infof(
            "Received ComfyUiPhotoMakeUpEyebrowsThumbnailsMessage, comfyUiPhotoMakeUpEyebrowsThumbnailsMessage: %s",
            comfyUiMultiPhotosAndPromptMessage
        );
        comfyUiManager.handleComfyUiPhotoMakeUpEyebrowsThumbnailsMessage(comfyUiMultiPhotosAndPromptMessage);
    }

    private void handleComfyUiPhotoAcePlusMessage(ComfyUiPhotoAcePlusMessage message) {
        Log.infof("Received ComfyUiPhotoAcePlusMessage, comfyUiPhotoAcePlusMessage: %s", message);
        comfyUiManager.handleComfyUiPhotoAcePlusMessage(message);
    }

    private void handleComfyUiVideoOnOutputAudioMessage(ComfyUiVideoAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiVideoOnOutputAudioMessage, comfyUiVideoAndAudioAndPromptMessage: %s", message);
        comfyUiManager.handleComfyUiVideoOnOutputAudioMessage(message);
    }

    private void handleComfyUiVideoOnOutputTextMessage(ComfyUiVideoAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiVideoOnOutputTextMessage, comfyUiVideoAndAudioAndPromptMessage: %s", message);
        comfyUiManager.handleComfyUiVideoOnOutputTextMessage(message);
    }

    private void handleComfyUiStillImageTextMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiStillImageTextMessage, comfyUiStillImageTextMessage: %s", message);
        comfyUiManager.handleComfyUiStillImageTextMessage(message);
    }

    private void handleComfyUiStillImageAudioMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiStillImageAudioMessage, comfyUiStillImageAudioMessage: %s", message);
        comfyUiManager.handleComfyUiStillImageAudioMessage(message);
    }

    private void handleComfyUiMmAudioMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        Log.infof("Received ComfyUiMmAudioMessage, comfyUiMmAudioMessage: %s", message);
        comfyUiManager.handleComfyUiMmAudioMessage(message);
    }

    private void handleComfyUiMusicGenMessage(ComfyUiMusicGenMessage message) {
        Log.infof("Received ComfyUiMusicGenMessage, comfyUiMusicGenMessage: %s", message);
        comfyUiManager.handleComfyUiMusicGenMessage(message);
    }
}
