package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.platform.timers.followerStats.FollowerStatsAggregationService;
import com.frever.platform.timers.redshift.RedshiftClient;
import com.frever.platform.timers.template.TemplateAggregationService;
import com.frever.platform.timers.videoKpi.VideoKpiAggregationService;
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@ApplicationScoped
@Unremovable
public class SqsReceiver {
    private static final int THREAD_POOL_SIZE = 20;
    SqsClient sqs;
    Thread consumerThread;
    @ConfigProperty(name = "queue.url")
    String queueUrl;
    @Inject
    TemplateAggregationService templateAggregationService;
    @Inject
    FollowerStatsAggregationService followerStatsAggregationService;
    @Inject
    VideoKpiAggregationService videoKpiAggregationService;
    @Inject
    RedshiftClient redshiftClient;
    @Inject
    ObjectMapper objectMapper;
    volatile boolean running = false;
    ExecutorService executor;

    @Startup
    void init() {
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        running = true;
        executor = newThreadPoolExecutor();
        consumerThread = Thread.ofPlatform().name("timers-sqs-receiver").start(this::consume);
    }

    public static ExecutorService newThreadPoolExecutor() {
        return new ThreadPoolExecutor(
            THREAD_POOL_SIZE,
            THREAD_POOL_SIZE,
            300L,
            java.util.concurrent.TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(THREAD_POOL_SIZE),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private void consume() {
        Log.infof("Starting to consume messages, queueUrl: %s", queueUrl);
        while (running) {
            try {
                sqs.receiveMessage(builder -> builder.queueUrl(queueUrl).waitTimeSeconds(20).maxNumberOfMessages(10))
                    .messages()
                    .forEach(message -> executor.submit(() -> handleSqsMessage(message)));
            } catch (Exception e) {
                Log.warnf(e, "Failed to consume messages from Sqs %s", queueUrl);
            }
        }
    }

    private void handleSqsMessage(Message message) {
        try {
            Log.infof("Received message from Sqs, body: %s, messageId: %s", message.body(), message.messageId());
            var snsMessage = objectMapper.readValue(message.body(), SnsMessage.class);
            String subject = snsMessage.subject();
            switch (subject) {
                case "VideoTemplateMappingMessage" -> handleVideoTemplateMappingMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    VideoTemplateMappingMessage.class
                ));
                case "GroupChangedMessage" -> handleGroupChangedMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    GroupChangedMessage.class
                ));
                case "TemplateUpdatedMessage" -> handleTemplateUpdatedMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    TemplateUpdatedMessage.class
                ));
                case "GroupDeletedMessage" -> handleGroupDeletedMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    GroupDeletedMessage.class
                ));
                case "GroupUnfollowedMessage" -> handleGroupUnfollowedMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    GroupUnfollowedMessage.class
                ));
                case "GroupFollowedMessage" -> handleGroupFollowedMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    GroupFollowedMessage.class
                ));
                case "VideoUnlikedMessage" -> handleVideoUnlikedMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    VideoUnlikedMessage.class
                ));
                case "OutfitChangedMessage" -> handleOutfitChangedMessage(objectMapper.readValue(
                    snsMessage.payload(),
                    OutfitChangedMessage.class
                ));
                case "RedshiftGroupUnfollowedMessage" ->
                    redshiftClient.handleGroupUnfollowedMessage(objectMapper.readValue(
                        snsMessage.payload(),
                        GroupUnfollowedMessage.class
                    ));
                default -> handleDefaultMessage(snsMessage.payload());
            }
            Log.infof("Done handling message from Sqs, messageId: %s", message.messageId());
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
                .visibilityTimeout(300));
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

    private void handleOutfitChangedMessage(OutfitChangedMessage outfitChangedMessage) {
        Log.infof(
            "Received OutfitChangedMessage, outfitId: %s, groupId: %s, createdTime: %s, modifiedTime: %s",
            outfitChangedMessage.id(),
            outfitChangedMessage.groupId(),
            outfitChangedMessage.createdTime(),
            outfitChangedMessage.modifiedTime()
        );
        redshiftClient.handleOutfitChangedMessage(outfitChangedMessage);
    }

    private void handleVideoUnlikedMessage(VideoUnlikedMessage videoUnlikedMessage) {
        Log.infof(
            "Received VideoUnlikedMessage, videoId: %s, groupId: %s, time: %s",
            videoUnlikedMessage.videoId(),
            videoUnlikedMessage.groupId(),
            videoUnlikedMessage.time()
        );
        videoKpiAggregationService.handleVideoUnlikedMessage(videoUnlikedMessage);
    }

    private void handleGroupUnfollowedMessage(GroupUnfollowedMessage groupUnfollowedMessage) {
        Log.infof(
            "Received GroupUnfollowedMessage, followerId: %s, followingId: %s, time: %s, isMutual: %s",
            groupUnfollowedMessage.followerId(),
            groupUnfollowedMessage.followingId(),
            groupUnfollowedMessage.time(),
            groupUnfollowedMessage.isMutual()
        );
        followerStatsAggregationService.handleGroupUnfollowedMessage(groupUnfollowedMessage);
        publishGroupUnfollowedForRedshift(groupUnfollowedMessage);
    }

    private void publishGroupUnfollowedForRedshift(GroupUnfollowedMessage groupUnfollowedMessage) {
        try {
            var message = SendMessageRequest.builder().queueUrl(queueUrl).messageBody(
                objectMapper.writeValueAsString(new SnsMessage(
                    "RedshiftGroupUnfollowedMessage",
                    UUID.randomUUID().toString(),
                    objectMapper.writeValueAsString(groupUnfollowedMessage),
                    groupUnfollowedMessage.time()
                ))
            ).build();
            sqs.sendMessage(message);
        } catch (Exception e) {
            Log.warnf(e, "Failed to publish GroupUnfollowedMessage for Redshift, followerId: %s, followingId: %s",
                groupUnfollowedMessage.followerId(), groupUnfollowedMessage.followingId()
            );
        }
    }

    private void handleGroupFollowedMessage(GroupFollowedMessage groupFollowedMessage) {
        Log.infof(
            "Received GroupFollowedMessage, followerId: %s, followingId: %s, time: %s, isMutual: %s",
            groupFollowedMessage.followerId(),
            groupFollowedMessage.followingId(),
            groupFollowedMessage.time(),
            groupFollowedMessage.isMutual()
        );
        redshiftClient.handleGroupFollowedMessage(groupFollowedMessage);
    }

    private void handleGroupDeletedMessage(GroupDeletedMessage groupDeletedMessage) {
        Log.infof("Received GroupDeletedMessage, groupId: %s", groupDeletedMessage.groupId());
        followerStatsAggregationService.handleGroupDeletedMessage(groupDeletedMessage);
    }

    private void handleTemplateUpdatedMessage(TemplateUpdatedMessage message) {
        Log.infof("Received TemplateUpdatedMessage, templateId: %s", message.templateId());
        templateAggregationService.handleTemplateUpdatedMessage(message);
    }

    private void handleVideoTemplateMappingMessage(VideoTemplateMappingMessage message) {
        Log.infof(
            "Received VideoTemplateMappingMessage, videoId: %s, newTemplateIds: %s, oldTemplateIds: %s",
            message.videoId(),
            Arrays.toString(message.newTemplateIds()),
            Arrays.toString(message.oldTemplateIds())
        );
        templateAggregationService.handleVideoTemplateMappingMessage(message);
    }

    private void handleGroupChangedMessage(GroupChangedMessage message) {
        Log.infof(
            "Received GroupChangedMessage, groupId: %s, mainCharacterId: %s, characterFiles: %s, country: %s, "
                + "language: %s",
            message.groupId(),
            message.mainCharacterId(),
            message.characterFiles(),
            message.taxationCountryId(),
            message.defaultLanguageId()
        );
        templateAggregationService.handleGroupChangedMessage(message);
    }

    private void handleDefaultMessage(String payload) {
        Log.warnf("Received message with unknown subject, payload: %s", payload);
        throw new MessageWithUnknownSubjectException();
    }

    @Shutdown
    void shutdown() {
        running = false;
        consumerThread.interrupt();
    }
}
