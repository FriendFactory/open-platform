package com.frever.ml.comfy.dto;

import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.*;

import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup;
import java.util.Set;

public enum ComfyUiWorkflow {
    PHOTO_PULID_2_WORKFLOW("photo-pulid-2", Pulid, "ComfyUiPhotoPulidMultiCharMessage"),
    PHOTO_PULID_3_WORKFLOW("photo-pulid-3", Pulid, "ComfyUiPhotoPulidMultiCharMessage"),
    FLUX_PHOTO_PROMPT_WORKFLOW("flux-photo-prompt", Pulid, "ComfyUiFluxPhotoPromptMessage"),
    FLUX_PROMPT_WORKFLOW("flux-prompt", Pulid, "ComfyUiFluxPromptMessage"),
    FLUX_PHOTO_REDUX_STYLE_WORKFLOW("flux-photo-redux-style", Pulid, "ComfyUiFluxPhotoReduxStyleMessage"),
    PHOTO_MAKE_UP_THUMBNAILS_WORKFLOW("photo-make-up-thumbnails", Makeup, "ComfyUiPhotoMakeUpThumbnailsMessage"),
    PHOTO_MAKE_UP_EYEBROWS_THUMBNAILS_WORKFLOW(
        "photo-make-up-eyebrows-thumbnails",
        Makeup,
        "ComfyUiPhotoMakeUpEyebrowsThumbnailsMessage"
    ),
    PHOTO_MAKE_UP_EYELASHES_EYESHADOW_THUMBNAILS_WORKFLOW(
        "photo-make-up-eyelashes-eyeshadow-thumbnails",
        Makeup,
        "ComfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage"
    ),
    PHOTO_MAKE_UP_LIPS_THUMBNAILS_WORKFLOW(
        "photo-make-up-lips-thumbnails",
        Makeup,
        "ComfyUiPhotoMakeUpLipsThumbnailsMessage"
    ),
    PHOTO_MAKE_UP_SKIN_THUMBNAILS_WORKFLOW(
        "photo-make-up-skin-thumbnails",
        Makeup,
        "ComfyUiPhotoMakeUpSkinThumbnailsMessage"
    ),
    SONIC_TEXT_WORKFLOW("sonic-text", LipSync, "ComfyUiSonicTextMessage"),
    SONIC_AUDIO_WORKFLOW("sonic-audio", LipSync, "ComfyUiSonicAudioMessage"),
    STILL_IMAGE_AUDIO_WORKFLOW("still-image-audio", Makeup, "ComfyUiStillImageAudioMessage"),
    STILL_IMAGE_TEXT_WORKFLOW("still-image-text", Makeup, "ComfyUiStillImageTextMessage"),
    PHOTO_ACE_PLUS_WORKFLOW("photo-ace-plus", Makeup, "ComfyUiPhotoAcePlusMessage"),
    VIDEO_LATENT_SYNC_WORKFLOW("latent-sync", LipSync, "ComfyUiLatentSyncMessage"),
    VIDEO_LATENT_SYNC_TEXT_WORKFLOW("latent-sync-text", LipSync, "ComfyUiLatentSyncTextMessage"),
    VIDEO_ON_OUTPUT_AUDIO_WORKFLOW("video-on-output-audio", Makeup, "ComfyUiVideoOnOutputAudioMessage"),
    VIDEO_ON_OUTPUT_TEXT_WORKFLOW("video-on-output-text", Makeup, "ComfyUiVideoOnOutputTextMessage"),
    MUSIC_GEN_WORKFLOW("music-gen", Makeup, "ComfyUiMusicGenMessage"),
    MM_AUDIO_WORKFLOW("mm-audio", Makeup, "ComfyUiMmAudioMessage"),
    VIDEO_LIVE_PORTRAIT_WORKFLOW("video-live-portrait", LipSync, "ComfyUiVideoLivePortraitMessage");

    public static final Set<ComfyUiWorkflow> PHOTO_WORKFLOWS_WITH_MULTIPLE_RESULTS = Set.of(
        FLUX_PHOTO_PROMPT_WORKFLOW,
        FLUX_PROMPT_WORKFLOW,
        FLUX_PHOTO_REDUX_STYLE_WORKFLOW,
        PHOTO_MAKE_UP_THUMBNAILS_WORKFLOW,
        PHOTO_PULID_2_WORKFLOW,
        PHOTO_PULID_3_WORKFLOW,
        PHOTO_ACE_PLUS_WORKFLOW
    );

    public static final Set<ComfyUiWorkflow> PHOTO_WORKFLOWS_WITH_MASK_RESULT = Set.of(
        PHOTO_MAKE_UP_EYEBROWS_THUMBNAILS_WORKFLOW,
        PHOTO_MAKE_UP_EYELASHES_EYESHADOW_THUMBNAILS_WORKFLOW,
        PHOTO_MAKE_UP_LIPS_THUMBNAILS_WORKFLOW,
        PHOTO_MAKE_UP_SKIN_THUMBNAILS_WORKFLOW
    );

    final String workflowName;
    final ComfyUiTaskInstanceSetup instanceSetup;
    final String sqsMessageSubject;

    ComfyUiWorkflow(String workflowName, ComfyUiTaskInstanceSetup instanceSetup, String sqsMessageSubject) {
        this.workflowName = workflowName;
        this.instanceSetup = instanceSetup;
        this.sqsMessageSubject = sqsMessageSubject;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public ComfyUiTaskInstanceSetup getInstanceSetup() {
        return instanceSetup;
    }

    public String getSqsMessageSubject() {
        return sqsMessageSubject;
    }

    public static boolean isPhotoMakeUpThumbnailFamilies(ComfyUiWorkflow workflow) {
        var workflowName = workflow.getWorkflowName();
        return workflowName.contains("make-up") && workflowName.contains("thumbnails");
    }

    public static ComfyUiWorkflow fromWorkflowName(String workflowName) {
        for (ComfyUiWorkflow workflow : values()) {
            if (workflow.getWorkflowName().equals(workflowName)) {
                return workflow;
            }
        }
        return null;
    }

    public static ComfyUiTaskInstanceSetup fromSqsSubjectToComfyUiTaskInstanceSetup(String sqsSubject) {
        for (ComfyUiWorkflow workflow : values()) {
            if (workflow.getSqsMessageSubject().equals(sqsSubject)) {
                return workflow.getInstanceSetup();
            }
        }
        return null;
    }
}
