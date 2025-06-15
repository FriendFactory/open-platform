package com.frever.ml.comfy.messaging.sqs;

import static com.frever.ml.comfy.dto.ComfyUiAutoscalingMessage.AutoScalingNotificationType.Ec2InstanceLaunch;
import static com.frever.ml.utils.Utils.addPortToServerIpIfNeeded;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.ComfyProperties;
import com.frever.ml.comfy.ComfyUiManager;
import com.frever.ml.comfy.dto.ComfyUiAutoscalingEvent;
import com.frever.ml.comfy.dto.ComfyUiAutoscalingMessage;
import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetupManager;
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.eclipse.microprofile.context.ManagedExecutor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

@Singleton
@Unremovable
public class ComfyUiAutoscalingEventSqsHandler extends AbstractSqsReceiver {
    private static final int AUTOSCALING_WAIT_TIME_BEFORE_CHECK = 90;
    private static final int AUTOSCALING_IGNORE_TIME = 900;

    @Inject
    ComfyProperties comfyProperties;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    ComfyUiManager comfyUiManager;
    @Inject
    ComfyUiTaskInstanceSetupManager taskInstanceSetupManager;

    Ec2Client ec2Client;

    @Override
    protected String getConsumerThreadName() {
        return "comfyui-autoscaling-event-handler";
    }

    @Override
    protected void logMessageConsumeStart() {
        Log.infof("Starting to consume messages about Autoscaling, queueUrl: %s", queueUrl);
    }

    @Override
    protected ManagedExecutor getTaskExecutor() {
        return null;
    }

    @Startup
    void init() {
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        queueUrl = comfyProperties.autoscalingEventQueueUrl();
        running = true;
        consumerThread = Thread.ofPlatform().name(getConsumerThreadName()).start(this::consume);
        ec2Client = Ec2Client.builder().region(Region.EU_CENTRAL_1).build();
    }

    @Shutdown
    void shutdown() {
        running = false;
        consumerThread.interrupt();
        if (sqs != null) {
            sqs.close();
        }
        if (ec2Client != null) {
            ec2Client.close();
        }
    }

    @Override
    protected void handleSqsMessage(Message message) {
        Log.debugf(
            "Autoscaling event queue received message, body: %s, messageId: %s",
            message.body(),
            message.messageId()
        );
        try {
            var comfyUiAutoscalingEvent = objectMapper.readValue(message.body(), ComfyUiAutoscalingEvent.class);
            var rawMessage = comfyUiAutoscalingEvent.message();
            var comfyUiAutoscalingMessage = objectMapper.readValue(rawMessage, ComfyUiAutoscalingMessage.class);
            var comfyUiAutoscalingNotificationType =
                ComfyUiAutoscalingMessage.fromNotificationTypeString(comfyUiAutoscalingMessage.event());
            if (Objects.requireNonNull(comfyUiAutoscalingNotificationType) == Ec2InstanceLaunch) {
                var happenedTime = comfyUiAutoscalingMessage.time();
                var instanceId = comfyUiAutoscalingMessage.instanceId();
                var now = Instant.now();
                if (now.minusSeconds(AUTOSCALING_WAIT_TIME_BEFORE_CHECK).isBefore(happenedTime)) {
                    Log.infof(
                        "Received EC2 instance launch event happened at %s, but probably ComfyUi service is up yet",
                        happenedTime
                    );
                    return;
                } else if (now.minusSeconds(AUTOSCALING_IGNORE_TIME).isAfter(happenedTime)) {
                    Log.infof("The message is too old, happened at %s, instanceId is %s", happenedTime, instanceId);
                    sqs.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                    return;
                }
                Log.infof(
                    "Received EC2 instance launch event, instanceId: %s, happened at: %s",
                    instanceId,
                    happenedTime
                );
                var describeInstancesResponse =
                    ec2Client.describeInstances(builder -> builder.instanceIds(instanceId));
                List<String> privateIps = describeInstancesResponse.reservations()
                    .stream()
                    .flatMap(reservation -> reservation.instances()
                        .stream()
                        .map(Instance::privateIpAddress)).toList();
                if (privateIps.size() != 1) {
                    Log.warnf(
                        "Received EC2 instance launch event, but weird, instanceId: %s, privateIps: %s",
                        instanceId, privateIps
                    );
                } else {
                    var comfyUiServerAddress = addPortToServerIpIfNeeded(privateIps.getFirst());
                    var setup =
                        taskInstanceSetupManager.getTaskInstanceSetupFromAutoscalingGroupName(comfyUiAutoscalingMessage.autoScalingGroupName());
                    Log.infof("Received EC2 instance launch event, comfyUiServerAddress: %s", comfyUiServerAddress);
                    comfyUiManager.cacheComponents(comfyUiServerAddress, setup);
                }
            }
            sqs.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        } catch (JacksonException e) {
            Log.warnf(
                e,
                "Failed to parse message from Sqs, messageId: %s, body: %s",
                message.messageId(),
                message.body()
            );
        }
    }
}
