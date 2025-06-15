package com.frever.ml.comfy;

import static com.frever.ml.comfy.ComfyUiTask.DEFAULT_DURATION;
import static com.frever.ml.comfy.dto.ComfyUiWorkflow.*;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.Pulid;
import static com.frever.ml.utils.Utils.generateComfyUiLatentSync;
import static com.frever.ml.utils.Utils.generateComfyUiMusicGen;
import static com.frever.ml.utils.Utils.generateComfyUiPhotoFlux;
import static com.frever.ml.utils.Utils.generateComfyUiPhotoFluxReduxStyle;
import static com.frever.ml.utils.Utils.generateComfyUiPhotoMakeup;
import static com.frever.ml.utils.Utils.generateComfyUiRequestWithInputAndAudio;
import static com.frever.ml.utils.Utils.generateComfyUiRequestWithInputAndPromptTextAndSeed;
import static com.frever.ml.utils.Utils.generateComfyUiRequestWithInputAndPromptTextAndTwoContextValues;

import com.frever.ml.comfy.dto.*;
import com.frever.ml.comfy.messaging.MediaConvertInfo;
import com.google.common.base.Strings;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

@ApplicationScoped
@Path("/comfyui")
public class ComfyResource {
    @Inject
    ComfyProperties comfyProperties;
    @Inject
    ComfyUiManager comfyUiManager;

    @GET
    @Path("/flow/list")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Flow> list() {
        return comfyUiManager.getFlows();
    }

    @POST
    @Path("/latentSyncAndMediaConvert")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse latentSync(@MultipartForm ComfyVideoAudioAndMediaConvertRequest request) {
        final String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(VIDEO_LATENT_SYNC_WORKFLOW);
        String workflowName = VIDEO_LATENT_SYNC_WORKFLOW.getWorkflowName();
        final String[] videoAndAudio = uploadComfyFiles(comfyIp, request, workflowName);
        String json = comfyUiManager.getWorkflow(workflowName);
        String postJson = generateComfyUiLatentSync(json, videoAndAudio, comfyUiManager.getClientId(), 5, 5);
        Log.debugf("PostJson: %s", postJson);
        var mediaConvertInfo =
            MediaConvertInfo.forTestInDevEnv(request.getVideoId(), request.getDestinationBucketPath());
        String fileName = request.getVideo().getFileName();
        long videoId = request.getVideoId();
        var comfyUiTask = ComfyUiTask.createVideoTask(
            VIDEO_LATENT_SYNC_WORKFLOW,
            comfyIp,
            fileName,
            UUID.randomUUID().toString(),
            DEFAULT_DURATION,
            -1,
            videoId,
            -1,
            "1.0"
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson, mediaConvertInfo);
    }

    @POST
    @Path("/sonic-text")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse sonicText(@MultipartForm ComfyInputAndAudioAndPromptRequest request) {
        final String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(SONIC_TEXT_WORKFLOW);
        final String input = uploadComfyFile(comfyIp, SONIC_TEXT_WORKFLOW.getWorkflowName(), request.getInput());
        String json = comfyUiManager.getWorkflow(SONIC_TEXT_WORKFLOW.getWorkflowName());
        String postJson = generateComfyUiRequestWithInputAndPromptTextAndSeed(
            json,
            input,
            request.getPromptText(),
            comfyUiManager.getClientId()
        );
        var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
            SONIC_TEXT_WORKFLOW,
            comfyIp,
            input,
            UUID.randomUUID().toString(),
            request.getGroupId()
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/sonic-audio")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse sonicAudio(@MultipartForm ComfyInputAndAudioAndPromptRequest request) {
        final String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(SONIC_AUDIO_WORKFLOW);
        final String input = uploadComfyFile(comfyIp, SONIC_AUDIO_WORKFLOW.getWorkflowName(), request.getInput());
        final String audio = uploadComfyFile(comfyIp, SONIC_AUDIO_WORKFLOW.getWorkflowName(), request.getAudio());
        String json = comfyUiManager.getWorkflow(SONIC_AUDIO_WORKFLOW.getWorkflowName());
        String postJson =
            generateComfyUiRequestWithInputAndAudio(
                json,
                List.of(input, audio),
                comfyUiManager.getClientId(),
                true,
                0,
                3
            );
        var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
            SONIC_AUDIO_WORKFLOW,
            comfyIp,
            input,
            UUID.randomUUID().toString(),
            request.getGroupId()
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/video-output-text")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse videoOutputText(@MultipartForm ComfyInputAndAudioAndPromptRequest request) {
        String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(VIDEO_ON_OUTPUT_TEXT_WORKFLOW);
        String input = uploadComfyFile(comfyIp, VIDEO_ON_OUTPUT_TEXT_WORKFLOW.getWorkflowName(), request.getInput());
        String json = comfyUiManager.getWorkflow(VIDEO_ON_OUTPUT_TEXT_WORKFLOW.getWorkflowName());
        String postJson = generateComfyUiRequestWithInputAndPromptTextAndTwoContextValues(
            json,
            input,
            request.getPromptText(),
            ContextSwitchSpeakerMode.HfAlpha,
            ContextSwitchLanguageMode.MandarinChinese,
            comfyUiManager.getClientId()
        );
        var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
            VIDEO_ON_OUTPUT_TEXT_WORKFLOW,
            comfyIp,
            input,
            UUID.randomUUID().toString(),
            request.getGroupId()
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/video-output-audio")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse videoOutputAudio(@MultipartForm ComfyInputAndAudioAndPromptRequest request) {
        String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(VIDEO_ON_OUTPUT_AUDIO_WORKFLOW);
        String input = uploadComfyFile(comfyIp, VIDEO_ON_OUTPUT_AUDIO_WORKFLOW.getWorkflowName(), request.getInput());
        String audio = uploadComfyFile(comfyIp, VIDEO_ON_OUTPUT_AUDIO_WORKFLOW.getWorkflowName(), request.getAudio());
        String json = comfyUiManager.getWorkflow(VIDEO_ON_OUTPUT_AUDIO_WORKFLOW.getWorkflowName());
        String postJson =
            generateComfyUiRequestWithInputAndAudio(json, List.of(input, audio), comfyUiManager.getClientId(), 0, 3);
        var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
            VIDEO_ON_OUTPUT_AUDIO_WORKFLOW,
            comfyIp,
            input,
            UUID.randomUUID().toString(),
            request.getGroupId()
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/music-gen")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse musicGen(@MultipartForm ComfyInputAndAudioAndPromptRequest request) {
        final String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(MUSIC_GEN_WORKFLOW);
        final String input = uploadComfyFile(comfyIp, MUSIC_GEN_WORKFLOW.getWorkflowName(), request.getInput());
        String json = comfyUiManager.getWorkflow(MUSIC_GEN_WORKFLOW.getWorkflowName());
        String postJson = generateComfyUiMusicGen(
            json,
            input,
            request.getPromptText(),
            MusicGenBackGroundMusic.MuteIncomingAudio,
            comfyUiManager.getClientId()
        );
        var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
            MUSIC_GEN_WORKFLOW,
            comfyIp,
            input,
            UUID.randomUUID().toString(),
            request.getGroupId()
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/comfyui-result")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ComfyUiResultResponse getComfyResult(ComfyUiResultRequest request) {
        return comfyUiManager.resultComfyUiResultFromS3(request);
    }

    @POST
    @Path("/photo-result")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ComfyUiResultResponse getPhotoResult(ComfyUiPhotoResultRequest request) {
        return comfyUiManager.resultPhotoInfoFromS3(request);
    }

    @POST
    @Path("/photo-multi-result")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ComfyUiMultiResultResponse getPhotoMultiResult(ComfyUiPhotoResultRequest request) {
        return comfyUiManager.resultPhotosInfoFromS3(request);
    }

    @POST
    @Path("/drop-tasks-in-sqs")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response dropTasksInSqs() {
        comfyUiManager.dropTasksInSqs();
        return Response.ok().build();
    }

    @POST
    @Path("/drop-tasks-in-comfyui-and-interrupt")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response dropTasksInComfyUiAndInterrupt() {
        comfyUiManager.dropTasksInComfyUiAndInterrupt();
        return Response.ok().build();
    }

    @POST
    @Path("/drop-all-tasks-and-interrupt")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response dropAllTasksAndInterrupt() {
        comfyUiManager.dropAllTasksAndInterrupt();
        return Response.ok().build();
    }

    private String uploadComfyFiles(String comfyIp, ComfyPhotoAndPromptRequest request, String workflowSymbol) {
        InputPart image = request.getImage();
        return uploadComfyFile(comfyIp, workflowSymbol, image);
    }

    private String uploadComfyFile(String comfyIp, String workflowSymbol, InputPart file) {
        if (Objects.isNull(file) || Strings.isNullOrEmpty(file.getFileName())) {
            Log.info("No file uploaded.");
            throw new BadRequestException("No file uploaded.");
        }
        try {
            return comfyUiManager.uploadComfyFile(
                comfyIp,
                generateFileName(file.getFileName(), workflowSymbol),
                file.getBody()
            );
        } catch (IOException e) {
            Log.errorf(e, "Failed to upload file %s", file.getFileName());
            throw new ServerErrorException("Failed to upload file to ComfyUI.", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private String[] uploadComfyFiles(String comfyIp, ComfyTwoPhotosAndPromptRequest request, String workflow) {
        InputPart input = request.getInput();
        InputPart makeup = request.getSource();
        if (Objects.isNull(input) || Objects.isNull(makeup)) {
            Log.info("No input or makeup uploaded.");
            throw new BadRequestException("No input or makeup uploaded.");
        }
        try {
            String inputName = comfyUiManager.uploadComfyFile(
                comfyIp,
                generateFileName(input.getFileName(), workflow),
                input.getBody()
            );
            String makeupName = comfyUiManager.uploadComfyFile(
                comfyIp,
                generateFileName(makeup.getFileName(), workflow),
                makeup.getBody()
            );
            return new String[]{inputName, makeupName};
        } catch (IOException e) {
            Log.errorf(
                e,
                "Failed to upload input %s and makeup %s",
                input.getFileName(),
                makeup.getFileName()
            );
            throw new ServerErrorException(
                "Failed to upload input and makeup to ComfyUI.",
                Response.Status.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String[] uploadComfyFiles(String comfyIp, ComfyVideoAndAuxiliaryFileRequest request, String workflowName) {
        InputPart auxiliaryFile = request.getAuxiliaryFile();
        InputPart video = request.getVideo();
        if ((Objects.isNull(auxiliaryFile) && !request.allowEmptyAuxiliaryFile()) || Objects.isNull(video)) {
            Log.info("No video or auxiliaryFile uploaded.");
            throw new BadRequestException("No video or auxiliaryFile uploaded.");
        }
        try {
            String auxiliaryFileName = "";
            if (auxiliaryFile != null) {
                auxiliaryFileName = comfyUiManager.uploadComfyFile(
                    comfyIp,
                    generateFileName(auxiliaryFile.getFileName(), workflowName),
                    auxiliaryFile.getBody()
                );
            }
            var videoName = comfyUiManager.uploadComfyFile(
                comfyIp,
                generateFileName(video.getFileName(), workflowName),
                video.getBody()
            );
            return new String[]{videoName, auxiliaryFileName};
        } catch (IOException e) {
            Log.errorf(
                e,
                "Failed to upload auxiliaryFile %s and video %s",
                auxiliaryFile.getFileName(),
                video.getFileName()
            );
            throw new ServerErrorException(
                "Failed to upload auxiliaryFile and video to ComfyUI.",
                Response.Status.INTERNAL_SERVER_ERROR
            );
        }
    }

    public static String generateFileName(String fileName, String workflow) {
        if (fileName.contains(".")) {
            String[] split = fileName.split("\\.");
            return split[0] + "-" + workflow + "." + split[1];
        } else {
            return fileName + workflow;
        }
    }

    @GET
    @Path("file-names")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getFileNames() {
        var videoFileNames = comfyUiManager.getFileNamesInQueue(comfyProperties.comfyUiLipSyncInstanceAddress());
        Log.infof("Video instance file names: %s", videoFileNames);
        var photoFileNames = comfyUiManager.getFileNamesInQueue(comfyProperties.comfyUiPulidInstanceAddress());
        Log.infof("Photo instance file names: %s", photoFileNames);
        return Response.ok().build();
    }

    @GET
    @Path("ongoing-prompts")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ongoingPrompts() {
        var ongoingPrompts = comfyUiManager.showOngoingPrompts();
        return Response.ok().entity(ongoingPrompts).build();
    }

    @POST
    @Path("/cache-components")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ComfyPromptResponse cacheComponents() {
        return comfyUiManager.cacheComponents(comfyProperties.comfyUiPulidInstanceAddress(), Pulid);
    }

    @POST
    @Path("/is-cached")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response isCached() {
        var cached = comfyUiManager.isComponentsCachedSync(comfyProperties.comfyUiPulidInstanceAddress(), Pulid);
        if (cached) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("/flux-photo-prompt")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse fluxPhotoPrompt(@MultipartForm ComfyPhotoAndPromptRequest request) {
        String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(FLUX_PHOTO_PROMPT_WORKFLOW);
        var workflow = FLUX_PHOTO_PROMPT_WORKFLOW.getWorkflowName();
        comfyUiManager.cacheComponents(comfyIp, FLUX_PHOTO_PROMPT_WORKFLOW.getInstanceSetup(), true);
        final String image = uploadComfyFiles(comfyIp, request, workflow);
        String json = comfyUiManager.getWorkflow(workflow);
        String postJson = generateComfyUiPhotoFlux(json, image, request.getPromptText(), comfyUiManager.getClientId());
        var comfyUiTask =
            ComfyUiTask.createTaskForWorkflow(
                ComfyUiWorkflow.FLUX_PHOTO_PROMPT_WORKFLOW,
                comfyIp,
                request.getImage().getFileName(),
                UUID.randomUUID().toString(),
                request.getGroupId()
            );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/flux-prompt")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ComfyPromptResponse fluxPrompt() {
        String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(FLUX_PROMPT_WORKFLOW);
        var workflow = FLUX_PROMPT_WORKFLOW.getWorkflowName();
        comfyUiManager.cacheComponents(comfyIp, FLUX_PROMPT_WORKFLOW.getInstanceSetup(), true);
        String json = comfyUiManager.getWorkflow(workflow);
        var groupId = 329L;
        String image = groupId + "-" + workflow;
        String postJson = generateComfyUiPhotoFlux(json, image, "", comfyUiManager.getClientId());
        var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
            ComfyUiWorkflow.FLUX_PHOTO_PROMPT_WORKFLOW,
            comfyIp,
            image,
            UUID.randomUUID().toString(),
            groupId
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/flux-photo-redux-style")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse fluxPhotoReduxStyle(@MultipartForm ComfyTwoPhotosAndPromptRequest request) {
        String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(FLUX_PHOTO_REDUX_STYLE_WORKFLOW);
        var workflow = FLUX_PHOTO_REDUX_STYLE_WORKFLOW.getWorkflowName();
        comfyUiManager.cacheComponents(comfyIp, FLUX_PHOTO_REDUX_STYLE_WORKFLOW.getInstanceSetup(), true);
        final String[] images = uploadComfyFiles(comfyIp, request, workflow);
        String json = comfyUiManager.getWorkflow(workflow);
        String postJson = generateComfyUiPhotoFluxReduxStyle(
            json,
            images[0],
            images[1],
            request.getPromptText(),
            comfyUiManager.getClientId()
        );
        var comfyUiTask =
            ComfyUiTask.createTaskForWorkflow(
                ComfyUiWorkflow.FLUX_PHOTO_REDUX_STYLE_WORKFLOW,
                comfyIp,
                request.getInput().getFileName(),
                UUID.randomUUID().toString(),
                request.getGroupId()
            );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/photo-make-up-thumbnails")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse photoMakeUpThumbnails(@MultipartForm ComfyTwoPhotosAndPromptRequest request) {
        String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(PHOTO_MAKE_UP_THUMBNAILS_WORKFLOW);
        String[] inputAndMakeup =
            uploadComfyFiles(comfyIp, request, PHOTO_MAKE_UP_THUMBNAILS_WORKFLOW.getWorkflowName());
        String json = comfyUiManager.getWorkflow(PHOTO_MAKE_UP_THUMBNAILS_WORKFLOW.getWorkflowName());
        String postJson = generateComfyUiPhotoMakeup(json, inputAndMakeup, comfyUiManager.getClientId());
        var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
            PHOTO_MAKE_UP_THUMBNAILS_WORKFLOW,
            comfyIp,
            request.getInput().getFileName(),
            UUID.randomUUID().toString(),
            request.getGroupId()
        );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

    @POST
    @Path("/photo-make-up-eyebrows-thumbnails")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ComfyPromptResponse photoMakeUpEyeBrowsThumbnails(@MultipartForm ComfyTwoPhotosAndPromptRequest request) {
        String comfyIp = comfyUiManager.getServerAddressForTaskWorkflow(PHOTO_MAKE_UP_EYEBROWS_THUMBNAILS_WORKFLOW);
        String[] inputAndMakeup =
            uploadComfyFiles(comfyIp, request, PHOTO_MAKE_UP_EYEBROWS_THUMBNAILS_WORKFLOW.getWorkflowName());
        String json = comfyUiManager.getWorkflow(PHOTO_MAKE_UP_EYEBROWS_THUMBNAILS_WORKFLOW.getWorkflowName());
        String postJson = generateComfyUiPhotoMakeup(json, inputAndMakeup, comfyUiManager.getClientId());
        var comfyUiTask =
            ComfyUiTask.createTaskForWorkflow(
                PHOTO_MAKE_UP_EYEBROWS_THUMBNAILS_WORKFLOW,
                comfyIp,
                request.getInput().getFileName(),
                UUID.randomUUID().toString(),
                request.getGroupId()
            );
        return comfyUiManager.submitComfyUiPrompt(comfyUiTask, postJson);
    }

}
