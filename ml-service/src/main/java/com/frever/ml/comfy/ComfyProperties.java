package com.frever.ml.comfy;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "comfy")
public interface ComfyProperties {
    String queueUrl();

    String lipSyncTaskQueueUrl();

    String pulidTaskQueueUrl();

    String makeupTaskQueueUrl();
    
    String autoscalingEventQueueUrl();

    String lipSyncTaskAutoScalingGroupName();

    String pulidTaskAutoScalingGroupName();

    String makeupTaskAutoScalingGroupName();

    String responseTopicArn();
    
    String resultS3Bucket();
    
    String comfyUiLipSyncInstanceAddress();
    
    String comfyUiPulidInstanceAddress();
    
    String comfyUiMakeupInstanceAddress();

}
