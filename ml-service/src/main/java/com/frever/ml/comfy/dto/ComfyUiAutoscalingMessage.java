package com.frever.ml.comfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComfyUiAutoscalingMessage(@JsonProperty("AccountId") String accountId,
                                        @JsonProperty("AutoScalingGroupName") String autoScalingGroupName,
                                        @JsonProperty("Event") String event,
                                        @JsonProperty("EC2InstanceId") String instanceId,
                                        @JsonProperty("Time") Instant time) {
    public enum AutoScalingNotificationType {
        Ec2InstanceLaunch, Ec2InstanceLaunchError, Ec2InstanceTerminate, Ec2InstanceTerminateError, TestNotification;
    }

    public static AutoScalingNotificationType fromNotificationTypeString(String notificationType) {
        if (notificationType == null || notificationType.isBlank()) {
            return null;
        }
        return switch (notificationType) {
            case "autoscaling:EC2_INSTANCE_LAUNCH" -> AutoScalingNotificationType.Ec2InstanceLaunch;
            case "autoscaling:EC2_INSTANCE_LAUNCH_ERROR" -> AutoScalingNotificationType.Ec2InstanceLaunchError;
            case "autoscaling:EC2_INSTANCE_TERMINATE" -> AutoScalingNotificationType.Ec2InstanceTerminate;
            case "autoscaling:EC2_INSTANCE_TERMINATE_ERROR" -> AutoScalingNotificationType.Ec2InstanceTerminateError;
            case "autoscaling:TEST_NOTIFICATION" -> AutoScalingNotificationType.TestNotification;
            default -> null;
        };
    }
}
