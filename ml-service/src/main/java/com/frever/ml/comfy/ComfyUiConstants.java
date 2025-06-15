package com.frever.ml.comfy;

import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup;
import java.util.Map;

public interface ComfyUiConstants {
    long SQS_POLL_BACKOFF_WHEN_CONNECTION_ISSUE = 10 * 1000;
    String PROTOCOL = "http://";
    int COMFYUI_TASK_INSTANCE_PORT = 8188;
    int MAX_TIMES_TO_STOP_POLLING_SQS = 4;
    Map<ComfyUiTaskInstanceSetup, Integer> COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING = Map.of(
        ComfyUiTaskInstanceSetup.LipSync, 3,
        ComfyUiTaskInstanceSetup.Pulid, 5,
        ComfyUiTaskInstanceSetup.Makeup, 5
    );
    Map<ComfyUiTaskInstanceSetup, Integer> COMFYUI_TASK_INSTANCE_SETUP_TO_NUMBER_OF_CHECKS_BEFORE_ACTION = Map.of(
        ComfyUiTaskInstanceSetup.LipSync, 3,
        ComfyUiTaskInstanceSetup.Pulid, 3,
        ComfyUiTaskInstanceSetup.Makeup, 3
    );
}
