package com.frever.ml.comfy.dto;

import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.LipSync;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.Makeup;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.Pulid;

import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup;

public enum CacheWorkflow {
    IS_CACHED_PHOTO_PULID_INSTANCE_WORKFLOW("is-cached-photo-pulid-instance", Pulid),
    CACHE_COMPONENTS_PHOTO_PULID_INSTANCE_WORKFLOW("cache-components-photo-pulid-instance", Pulid),
    IS_CACHED_PHOTO_MAKEUP_INSTANCE_WORKFLOW("is-cached-photo-makeup-instance", Makeup),
    CACHE_COMPONENTS_PHOTO_MAKEUP_INSTANCE_WORKFLOW("cache-components-photo-makeup-instance", Makeup),
    IS_CACHED_VIDEO_LIP_SYNC_INSTANCE_WORKFLOW("is-cached-video-lip-sync-instance", LipSync),
    CACHE_COMPONENTS_VIDEO_LIP_SYNC_INSTANCE_WORKFLOW("cache-components-video-lip-sync-instance", LipSync);

    final String workflowName;
    final ComfyUiTaskInstanceSetup instanceSetup;

    CacheWorkflow(String workflowName, ComfyUiTaskInstanceSetup instanceSetup) {
        this.workflowName = workflowName;
        this.instanceSetup = instanceSetup;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public ComfyUiTaskInstanceSetup getInstanceSetup() {
        return instanceSetup;
    }
}
