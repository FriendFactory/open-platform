package com.frever.ml.comfy.setup;

import com.frever.ml.comfy.dto.CacheWorkflow;

public enum ComfyUiTaskInstanceSetup {
    LipSync, Pulid, Makeup;

    public CacheWorkflow getCacheComponentsWorkflow() {
        return switch (this) {
            case LipSync -> CacheWorkflow.CACHE_COMPONENTS_VIDEO_LIP_SYNC_INSTANCE_WORKFLOW;
            case Pulid -> CacheWorkflow.CACHE_COMPONENTS_PHOTO_PULID_INSTANCE_WORKFLOW;
            case Makeup -> CacheWorkflow.CACHE_COMPONENTS_PHOTO_MAKEUP_INSTANCE_WORKFLOW;
        };
    }

    public CacheWorkflow getCheckCacheWorkflow() {
        return switch (this) {
            case LipSync -> CacheWorkflow.IS_CACHED_VIDEO_LIP_SYNC_INSTANCE_WORKFLOW;
            case Pulid -> CacheWorkflow.IS_CACHED_PHOTO_PULID_INSTANCE_WORKFLOW;
            case Makeup -> CacheWorkflow.IS_CACHED_PHOTO_MAKEUP_INSTANCE_WORKFLOW;
        };
    }
}
