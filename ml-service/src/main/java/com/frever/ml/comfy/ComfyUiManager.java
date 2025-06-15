package com.frever.ml.comfy;

import static com.frever.ml.comfy.ComfyUiConstants.PROTOCOL;
import static com.frever.ml.comfy.ComfyUiResponsePublisher.FAILED_TO_FIND_RESULT;
import static com.frever.ml.comfy.dto.ComfyUiPhotoResultRequest.toComfyUiResultRequest;
import static com.frever.ml.comfy.dto.ComfyUiWorkflow.*;
import static com.frever.ml.utils.Utils.*;
import static com.github.benmanes.caffeine.cache.Caffeine.newBuilder;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.dto.*;
import com.frever.ml.comfy.messaging.*;
import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup;
import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetupManager;
import com.frever.ml.dao.ComfyUiTaskDao;
import com.frever.ml.messaging.DelayMessageHandlingException;
import com.frever.ml.messaging.SnsMessage;
import com.frever.ml.utils.Utils;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.quarkus.arc.Lock;
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.http.conn.ConnectTimeoutException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;

@Singleton
@Unremovable
public class ComfyUiManager {
    protected static final String CLIENT_ID = UUID.randomUUID().toString();
    protected static final Cache<String, List<Flow>> CACHE = newBuilder()
        .expireAfterWrite(60, MINUTES)
        .build();
    protected static final Cache<String, String> WORKFLOW_CACHE = newBuilder()
        .expireAfterWrite(60, MINUTES)
        .build();
    public static final String RESULT_S3_KEY_PREFIX = "comfyui-results";
    public static final Set<String> SUPPORTED_FILE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "mp4", "mp3", "webp");
    public static final String THUMBNAIL_MAIN_FORMAT = "_608x1080";
    public static final String THUMBNAIL_COVER_FORMAT = "_608x418";
    public static final String THUMBNAIL_THUMBNAIL_FORMAT = "_128x128";
    public static final String MASK_FORMAT = "_mask";
    protected static final int CONNECTIVITY_CHECK_INTERVAL = 30;
    protected static final int MAX_PROMPT_WAIT_TIME = 60 * 15;
    protected static final int ONGOING_PROMPT_HANDOVER_MESSAGE_DELAY = 120;
    protected static final int SOFT_WEB_SOCKET_RECONNECT_INTERVAL_SECONDS = 20 * 60;
    protected static final int HARD_WEB_SOCKET_RECONNECT_INTERVAL_SECONDS = 60 * 60;
    public static final String CACHE_BACKEND_DATA_CLASS_TYPE = "CacheBackendData //Inspire";
    public static final String COMFY_CLASS_TYPE = "class_type";
    protected static volatile boolean RUNNING;

    protected final ConcurrentMap<String, OngoingPromptRequest> ongoingPrompts = new ConcurrentHashMap<>();
    protected final ConcurrentMap<String, Session> serverAddrToComfyUiSessions = new ConcurrentHashMap<>();
    protected final ConcurrentMap<String, Instant> lastSessionConnected = new ConcurrentHashMap<>();
    protected final ConcurrentMap<String, Boolean> componentsCached = new ConcurrentHashMap<>();
    protected final ConcurrentMap<String, CountDownLatch> pendingResult = new ConcurrentHashMap<>();

    protected Set<String> allWorkflows;

    protected volatile String currentRunningPromptId;
    protected S3Client s3;
    protected SqsClient sqs;
    protected String resultS3Bucket;

    private ComfyUiMultiResultResponse comfyUiMultiResultResponseNotFound;
    private ComfyUiResultResponse comfyUiResultResponseNotFound;

    @Inject
    protected ObjectMapper objectMapper;
    @Inject
    protected Client client;
    @Inject
    protected ComfyUiTaskDao comfyUiTaskDao;
    @Inject
    protected ComfyProperties comfyProperties;
    @Inject
    ComfyUiWebSocketClient comfyUiWebSocketClient;
    @Inject
    ComfyUiTaskInstanceSetupManager comfyUiTaskInstanceSetupManager;
    @Inject
    ComfyUiResponsePublisher comfyUiResponsePublisher;

    public String getClientId() {
        return CLIENT_ID;
    }

    @PostConstruct
    protected void init() {
        RUNNING = true;
        connectToAllTaskInstanceServers();
        s3 = S3Client.builder().region(Region.EU_CENTRAL_1).build();
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        allWorkflows = getFlows().stream().map(Flow::name).collect(toSet());
        resultS3Bucket = comfyProperties.resultS3Bucket();
        comfyUiMultiResultResponseNotFound =
            new ComfyUiMultiResultResponse(resultS3Bucket, "not_found", null, null, null);
        comfyUiResultResponseNotFound = new ComfyUiResultResponse(resultS3Bucket, "not_found");
    }

    private void connectToAllTaskInstanceServers() {
        if (!isDev() && !isProd()) {
            Log.info("Not in dev or prod, most probably running tests, skipping connect to ComfyUi servers.");
            return;
        }
        for (var serverEntry : serverAddrToComfyUiSessions.entrySet()) {
            var serverAddress = serverEntry.getKey();
            var session = serverEntry.getValue();
            if (!session.isOpen()) {
                Log.infof(
                    "Server session is not open, removing it from serverAddrToComfyUiSessions: %s",
                    serverAddress
                );
                serverAddrToComfyUiSessions.remove(serverAddress);
            }
        }
        var lipSyncServerAddresses = comfyUiTaskInstanceSetupManager.getLipSyncServerAddresses();
        var pulidServerAddresses = comfyUiTaskInstanceSetupManager.getPulidServerAddresses();
        var makeupServerAddresses = comfyUiTaskInstanceSetupManager.getMakeupServerAddresses();
        for (var lipSyncServerAddress : lipSyncServerAddresses) {
            if (serverAddrToComfyUiSessions.containsKey(lipSyncServerAddress)) {
                continue;
            }
            var lipSyncSession = connectComfyUiWebsockets(lipSyncServerAddress, comfyUiWebSocketClient);
            if (lipSyncSession != null) {
                serverAddrToComfyUiSessions.put(lipSyncServerAddress, lipSyncSession);
                componentsCached.put(lipSyncServerAddress, false);
            } else {
                Log.warnf(
                    "Failed to connect to ComfyUi lip-sync server: %s, will not be able to send prompts.",
                    lipSyncServerAddress
                );
            }
        }
        for (var pulidServerAddress : pulidServerAddresses) {
            if (serverAddrToComfyUiSessions.containsKey(pulidServerAddress)) {
                continue;
            }
            var pulidSession = connectComfyUiWebsockets(pulidServerAddress, comfyUiWebSocketClient);
            if (pulidSession != null) {
                serverAddrToComfyUiSessions.put(pulidServerAddress, pulidSession);
                componentsCached.put(pulidServerAddress, false);
            } else {
                Log.warnf(
                    "Failed to connect to ComfyUi pulid server: %s, will not be able to send prompts.",
                    pulidServerAddress
                );
            }
        }
        for (var makeupServerAddress : makeupServerAddresses) {
            if (serverAddrToComfyUiSessions.containsKey(makeupServerAddress)) {
                continue;
            }
            var makeupSession = connectComfyUiWebsockets(makeupServerAddress, comfyUiWebSocketClient);
            if (makeupSession != null) {
                serverAddrToComfyUiSessions.put(makeupServerAddress, makeupSession);
                componentsCached.put(makeupServerAddress, false);
            } else {
                Log.warnf(
                    "Failed to connect to ComfyUi makeup server: %s, will not be able to send prompts.",
                    makeupServerAddress
                );
            }
        }
    }

    @PreDestroy
    protected void shutdown() {
        RUNNING = false;
        sendPromptHandoverMessageIfThereAreOngoingPrompts();
        closeWebSocketSessions();
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                Log.warnf(e, "Failed to close client");
            }
        }
        if (s3 != null) {
            try {
                s3.close();
            } catch (Exception e) {
                Log.warnf(e, "Failed to close s3Client");
            }
        }
        if (sqs != null) {
            try {
                sqs.close();
            } catch (Exception e) {
                Log.warnf(e, "Failed to close sqsClient");
            }
        }
    }

    @Scheduled(every = "3m", delay = 1)
    @Lock(value = Lock.Type.WRITE, time = CONNECTIVITY_CHECK_INTERVAL, unit = TimeUnit.SECONDS)
    void checkSessionConnectivity() {
        if (!RUNNING) {
            return;
        }
        connectToAllTaskInstanceServers();
        triggerWebSocketReconnectIfTooLong();
    }

    @Scheduled(every = "1m", delay = 1)
    @Lock(value = Lock.Type.WRITE, time = CONNECTIVITY_CHECK_INTERVAL, unit = TimeUnit.SECONDS)
    void checkOngoingPromptsForPulidServers() {
        var pulidServerAddresses = comfyUiTaskInstanceSetupManager.getPulidServerAddresses();
        for (var pulidServerAddress : pulidServerAddresses) {
            checkOngoingPrompts(pulidServerAddress);
        }
    }

    @Scheduled(every = "1m", delay = 1)
    @Lock(value = Lock.Type.WRITE, time = CONNECTIVITY_CHECK_INTERVAL, unit = TimeUnit.SECONDS)
    void checkOngoingPromptsForMakeupServers() {
        var makeupServerAddresses = comfyUiTaskInstanceSetupManager.getMakeupServerAddresses();
        for (var makeupServerAddress : makeupServerAddresses) {
            checkOngoingPrompts(makeupServerAddress);
        }
    }

    @Scheduled(every = "1m", delay = 1)
    @Lock(value = Lock.Type.WRITE, time = CONNECTIVITY_CHECK_INTERVAL, unit = TimeUnit.SECONDS)
    void checkOngoingPromptsForLipSyncServers() {
        var lipSyncServerAddresses = comfyUiTaskInstanceSetupManager.getLipSyncServerAddresses();
        for (var lipSyncServerAddress : lipSyncServerAddresses) {
            checkOngoingPrompts(lipSyncServerAddress);
        }
    }

    void onPromptCompleted(@Observes ComfyUiPromptCompleted event) {
        var ongoingPromptRequest = ongoingPrompts.get(event.promptId());
        if (ongoingPromptRequest == null) {
            Log.infof("PromptCompleted, Prompt %s is not in ongoingPrompts, will ignore it.", event.promptId());
            return;
        }
        grabGeneratedResultAndUpload(
            event.serverIpAndPort(),
            event.promptId(),
            ongoingPromptRequest.partialName(),
            ongoingPromptRequest.mediaConvertInfo(),
            ongoingPromptRequest.resultType()
        );
    }

    void onComfyExecutionErrorEvent(@Observes ComfyExecutionError event) {
        var ongoingPromptRequest = ongoingPrompts.get(event.promptId());
        if (ongoingPromptRequest == null) {
            Log.infof("ComfyExecutionError, Prompt %s is not in ongoingPrompts, will ignore it.", event.promptId());
            return;
        }
        comfyUiResponsePublisher.failToGenerateResult(
            ongoingPromptRequest.partialName(),
            "ComfyUi side error: " + event.exceptionMessage()
        );
    }

    void onSessionClosed(@Observes ComfyUiSessionClosed event) {
        if (!RUNNING) {
            return;
        }
        Log.infof("Got ComfyUiSessionClosed event, reconnecting...");
        var serverAddress = event.serverAddress();
        var videoServerSession = connectComfyUiWebsockets(serverAddress, comfyUiWebSocketClient);
        if (videoServerSession != null) {
            serverAddrToComfyUiSessions.put(serverAddress, videoServerSession);
        }
    }

    void onCloseSessionSignal(@Observes ComfyUiCloseSessionSignal event) {
        if (!RUNNING) {
            return;
        }
        Log.info("Got ComfyUiCloseSessionSignal, closing session...");
        var serverAddress = event.serverAddress();
        var session = serverAddrToComfyUiSessions.get(serverAddress);
        closeWebSocketSession(serverAddress, session);
    }

    protected void closeWebSocketSessions() {
        for (var session : serverAddrToComfyUiSessions.entrySet()) {
            closeWebSocketSession(session.getKey(), session.getValue());
        }
    }

    protected void closeWebSocketSession(String serverAddress, Session session) {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                serverAddrToComfyUiSessions.remove(serverAddress);
            } catch (IOException e) {
                Log.warnf(e, "Failed to close session");
            }
        }
    }

    protected void sendPromptHandoverMessageIfThereAreOngoingPrompts() {
        if (ongoingPrompts.isEmpty()) {
            return;
        }
        ongoingPrompts.forEach((promptId, request) -> {
            try {
                var serverIpAndPort = addPortToServerIpIfNeeded(request.comfyIp());
                String payload =
                    objectMapper.writeValueAsString(new OngoingPromptHandoverMessage(
                        serverIpAndPort,
                        promptId,
                        request.fileName(),
                        request.partialName(),
                        request.submittedAt(),
                        request.mediaConvertInfo(),
                        request.resultType()
                    ));
                SnsMessage message = new SnsMessage(
                    "OngoingPromptHandoverMessage",
                    UUID.randomUUID().toString(),
                    payload,
                    Instant.now()
                );
                String json = objectMapper.writeValueAsString(message);
                sqs.sendMessage(builder -> builder.queueUrl(comfyProperties.queueUrl())
                    .messageBody(json).delaySeconds(ONGOING_PROMPT_HANDOVER_MESSAGE_DELAY));
            } catch (JsonProcessingException e) {
                Log.warnf(
                    e,
                    "Failed to serialize ServiceShutDown. ClientId: %s, OngoingPrompts: %s",
                    getClientId(),
                    ongoingPrompts.keySet()
                );
            }
        });
    }

    Session connectComfyUiWebsockets(String serverAddress, Object websocketClient) {
        if (!RUNNING) {
            Log.info("Not running, skipping connect to ComfyUi websockets");
            return null;
        }
        var uri = "ws://" + serverAddress + "/ws?clientId=" + getClientId();
        var lastSessionConnect = lastSessionConnected.get(serverAddress);
        if (lastSessionConnect != null && Instant.now()
            .isBefore(lastSessionConnect.plusSeconds(CONNECTIVITY_CHECK_INTERVAL / 3))) {
            Log.infof("Skipping connect to ComfyUi websockets, last connect was %s", lastSessionConnect);
            return null;
        }
        try {
            Log.infof("Connecting to ComfyUi websockets, uri: %s", uri);
            return ContainerProvider.getWebSocketContainer().connectToServer(websocketClient, URI.create(uri));
        } catch (DeploymentException | IOException e) {
            Log.warnf("Failed to connect to %s: %s", uri, e.getMessage());
            return null;
        } finally {
            lastSessionConnect = Instant.now();
            lastSessionConnected.put(serverAddress, lastSessionConnect);
        }
    }

    protected void putIntoOngoingPrompts(
        ComfyUiTask comfyUiTask,
        String postJson,
        ComfyPromptResponse promptResponse,
        MediaConvertInfo mediaConvertInfo
    ) {
        var promptId = UUID.fromString(promptResponse.promptId());
        comfyUiTask.setPromptId(promptId);
        long id;
        if (comfyUiTask.getId() != 0) {
            id = comfyUiTask.getId();
        } else {
            id = comfyUiTaskDao.createTask(comfyUiTask);
        }
        ComfyUiResultType resultType = getComfyUiResultType(comfyUiTask);
        var ongoingPromptRequest = new OngoingPromptRequest(
            comfyUiTask.getServerIp(),
            comfyUiTask.getFileName(),
            comfyUiTask.getPartialName(),
            postJson,
            Instant.now(),
            id,
            mediaConvertInfo,
            resultType
        );
        ongoingPrompts.put(promptId.toString(), ongoingPromptRequest);
        Log.infof("Added prompt %s to ongoingPrompts for server: %s", promptId, comfyUiTask.getServerIp());
    }

    private static ComfyUiResultType getComfyUiResultType(ComfyUiTask comfyUiTask) {
        ComfyUiResultType resultType;
        if (comfyUiTask.getWorkflow().contains("pulid") || comfyUiTask.getWorkflow().contains("thumbnails")
            || comfyUiTask.getWorkflow().startsWith("flux") || comfyUiTask.getWorkflow().contains("ace-plus")) {
            resultType = ComfyUiResultType.ImageJpg;
        } else if (comfyUiTask.getWorkflow().startsWith("photo")) {
            resultType = ComfyUiResultType.Image;
        } else if (comfyUiTask.getWorkflow().equals("is-cached")) {
            resultType = ComfyUiResultType.IsCached;
        } else if (comfyUiTask.getWorkflow().equals("cache-components")) {
            resultType = ComfyUiResultType.CacheComponents;
        } else if (comfyUiTask.getWorkflow().contains("sonic")) {
            resultType = ComfyUiResultType.Video;
        } else {
            resultType = ComfyUiResultType.Video;
        }
        return resultType;
    }

    protected OngoingPromptRequest removeFromOngoingPrompts(String promptId) {
        var ongoingPrompt = ongoingPrompts.remove(promptId);
        if (ongoingPrompt != null) {
            Log.infof("Removed prompt %s from ongoingPrompts", promptId);
            var id = ongoingPrompt.comfyUiTaskId();
            comfyUiTaskDao.markTaskFinished(id);
        } else {
            Log.infof("Most probably we are handling handled-over message here, prompt %s", promptId);
            comfyUiTaskDao.markTaskFinished(UUID.fromString(promptId));
        }
        return ongoingPrompt;
    }

    public String getServerAddressForTaskWorkflow(ComfyUiWorkflow comfyUiWorkflow) {
        if (comfyUiWorkflow == null) {
            throw new IllegalArgumentException("Workflow is null.");
        }
        return comfyUiTaskInstanceSetupManager.getServerAddressFromWorkflow(comfyUiWorkflow);
    }

    protected boolean grabGeneratedResultAndUpload(
        String serverAddress,
        String promptId,
        String partialName,
        MediaConvertInfo mediaConvertInfo,
        ComfyUiResultType resultType
    ) {
        Log.infof("Fetching history for prompt %s to server %s", promptId, serverAddress);
        try (Response response = client.target(PROTOCOL + serverAddress + "/history/" + promptId)
            .request()
            .get()) {
            var entity = response.readEntity(String.class);
            if (response.getStatus() != 200) {
                Log.warnf(
                    "Failed to get history for prompt %s, status: %d, result: %s",
                    promptId,
                    response.getStatus(),
                    entity
                );
                if (response.getStatus() == 404 && currentRunningPromptId != null && currentRunningPromptId.equals(
                    promptId)) {
                    var ongoingPrompt = removeFromOngoingPrompts(promptId);
                    if (ongoingPrompt != null) {
                        Log.infof(
                            "Prompt %s is not found in the history, and is equal to currentRunningPromptId, remove it"
                                + " from ongoingPrompts, as most probably it caused ComfyUi crash.",
                            promptId
                        );
                    }
                    return true;
                }
                comfyUiResponsePublisher.failToGenerateResult(partialName, FAILED_TO_FIND_RESULT);
                return false;
            }
            JsonNode jsonNode = objectMapper.readTree(entity);
            Log.debugf("history response: %s", jsonNode);
            JsonNode outputs = jsonNode.findValue("outputs");
            if (outputs == null) {
                Log.warnf("No outputs JsonNode found for prompt %s, response: %s", promptId, entity);
                comfyUiResponsePublisher.failToGenerateResult(partialName, FAILED_TO_FIND_RESULT);
                return false;
            }
            handleHistoryOutputBasedOnComfyUiResultType(
                serverAddress,
                promptId,
                partialName,
                mediaConvertInfo,
                resultType,
                outputs,
                jsonNode
            );
            var ongoingPrompt = removeFromOngoingPrompts(promptId);
            if (ongoingPrompt == null) {
                return true;
            }
            Instant submittedAt = ongoingPrompt.submittedAt();
            if (submittedAt != null) {
                Log.infof(
                    "Prompt %s completed in %s seconds (including time spent waiting in queue).",
                    promptId,
                    Instant.now().getEpochSecond() - submittedAt.getEpochSecond()
                );
            }
            return true;
        } catch (JacksonException e) {
            Log.warnf(e, "Failed to parse history response for prompt %s", promptId);
            comfyUiResponsePublisher.failToGenerateResult(partialName, FAILED_TO_FIND_RESULT);
            return false;
        }
    }

    private void handleHistoryOutputBasedOnComfyUiResultType(
        String serverAddress,
        String promptId,
        String partialName,
        MediaConvertInfo mediaConvertInfo,
        ComfyUiResultType resultType,
        JsonNode outputs,
        JsonNode jsonNode
    ) {
        if (resultType == ComfyUiResultType.ImageJpg) {
            List<String> s3Keys = outputs.findValues("images").stream()
                .map(image -> fetchResultAndNotifyMediaConvert(
                    client,
                    serverAddress,
                    partialName,
                    image,
                    mediaConvertInfo,
                    resultType
                )).filter(s3Key -> s3Key != null && !s3Key.isBlank()).toList();
            if (!s3Keys.isEmpty()) {
                comfyUiResponsePublisher.resultGeneratedSuccessfully(partialName, s3Keys);
            }
        } else if (resultType == ComfyUiResultType.IsCached) {
            outputs.findValues("text");
            JsonNode text = outputs.findValue("text");
            Boolean cached = Boolean.parseBoolean(text.get(0).asText());
            componentsCached.put(serverAddress, cached);
            releaseWaitingPrompt(promptId);
        } else if (resultType == ComfyUiResultType.CacheComponents) {
            var outputsEmpty = outputs.isEmpty();
            JsonNode status = jsonNode.findPath("status");
            String statusStr = status.findValue("status_str").asText();
            boolean completed = status.findValue("completed").asBoolean();
            if (!outputsEmpty || !completed || !statusStr.equals("success")) {
                Log.warnf(
                    "Something wrong with CacheComponents. PromptId: %s, history response: %s",
                    promptId,
                    jsonNode
                );
            }
            releaseWaitingPrompt(promptId);
        } else {
            List<String> s3Keys = outputs.findValues("gifs").stream()
                .map(gif -> fetchResultAndNotifyMediaConvert(
                    client,
                    serverAddress,
                    partialName,
                    gif,
                    mediaConvertInfo,
                    resultType
                )).filter(s3Key -> s3Key != null && !s3Key.isBlank()).toList();
            if (!s3Keys.isEmpty()) {
                comfyUiResponsePublisher.resultGeneratedSuccessfully(partialName, s3Keys);
            }
        }
    }

    protected String fetchResultAndNotifyMediaConvert(
        Client client,
        String serverAddress,
        String partialName,
        JsonNode node,
        MediaConvertInfo mediaConvertInfo,
        ComfyUiResultType resultType
    ) {
        Log.infof("JsonNode: %s", node);
        JsonNode jsonNode = node.get(0);
        String filename = jsonNode.get(resultType.getJsonNode()).asText();
        String subFolder = jsonNode.get("subfolder").asText();
        String type = jsonNode.get("type").asText();
        if (type != null && type.equals("temp")) {
            Log.infof("Skipping temp file: %s", filename);
            return "";
        }
        try (
            Response response = client.target(PROTOCOL + serverAddress + "/view?")
                .queryParam("filename", filename)
                .queryParam("subfolder", subFolder)
                .queryParam("type", type)
                .request()
                .get()) {
            if (response.getStatus() != 200) {
                Log.warnf(
                    "Failed to fetch image for filename %s, subFolder %s, type %s, status: %d",
                    filename,
                    subFolder,
                    type,
                    response.getStatus()
                );
                comfyUiResponsePublisher.failToGenerateResult(partialName, FAILED_TO_FIND_RESULT);
                return "";
            }
            byte[] entity = response.readEntity(byte[].class);
            String s3Key = RESULT_S3_KEY_PREFIX + "/" + filename;
            s3.putObject(
                builder -> builder.bucket(resultS3Bucket).key(s3Key).acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL),
                RequestBody.fromBytes(entity)
            );
            Log.infof("Uploaded generated video to S3, bucket: %s, key: %s", resultS3Bucket, s3Key);
            if (mediaConvertInfo != null) {
                Log.infof(
                    "Will create MediaConvert task for video: %s, destPath: %s",
                    mediaConvertInfo.videoId(),
                    mediaConvertInfo.destinationBucketPath()
                );
                createMediaConvertTask(mediaConvertInfo, s3Key);
            }
            return s3Key;
        }
    }

    protected void createMediaConvertTask(MediaConvertInfo mediaConvertInfo, String key) {
        String destinationBucketPath = mediaConvertInfo.destinationBucketPath();
        if (destinationBucketPath == null || destinationBucketPath.isBlank()) {
            Log.infof("DestinationBucketPath is null or blank, will not create MediaConvert task.");
            return;
        }
        var keyAndPath = getDestinationS3BucketAndKey(destinationBucketPath);
        var destinationKey = Utils.normalizeS3Path(keyAndPath[1]) + "video.mp4";
        s3.copyObject(builder -> builder.sourceBucket(resultS3Bucket)
            .sourceKey(key)
            .destinationBucket(keyAndPath[0])
            .destinationKey(destinationKey)
            .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL));
        Log.infof(
            "Copied video from %s/%s to %s/%s",
            resultS3Bucket,
            key,
            keyAndPath[0],
            destinationKey
        );
        var sourceBucketPath = "s3://" + keyAndPath[0] + "/" + destinationKey;
        var mediaConvertJobs =
            CreateMediaConvertJobMessage.fromMediaConvertInfo(mediaConvertInfo, sourceBucketPath);
        var queue = mediaConvertInfo.mediaConvertJobSqsQueue();
        for (var job : mediaConvertJobs) {
            try {
                var messageBody = objectMapper.writeValueAsString(job);
                sqs.sendMessage(builder -> builder.queueUrl(queue).messageBody(messageBody));
                Log.infof("Sent MediaConvert job to queue %s: %s", queue, job);
            } catch (JsonProcessingException e) {
                Log.warnf(e, "Failed to send MediaConvert job to queue %s: %s", queue, job);
            }
        }
    }

    public int comfyUiQueueTimeEstimationSeconds(String serverIp) {
        var remainingTasks = comfyUiTaskDao.getComfyUiUnfinishedTaskDurationItems(serverIp);
        Log.infof("Got %d remaining tasks for server %s", remainingTasks.size(), serverIp);
        int totalDuration = 0;
        for (var task : remainingTasks) {
            int timeEstimation =
                taskDurationEstimationSeconds(task.promptId(), task.workflow(), task.duration(), task.startedAt());
            totalDuration += timeEstimation;
        }
        return totalDuration;
    }

    public boolean fileExistsInQueue(String serverAddress, String fileName) {
        return getFileNamesInQueue(serverAddress).contains(fileName);
    }

    protected Set<String> getPromptInQueue(String serverAddress) {
        try (var response = client.target(PROTOCOL + serverAddress + "/queue")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
            if (response.getStatus() != 200) {
                Log.warn("Failed to get queue status. " + response.readEntity(String.class));
                return Collections.emptySet();
            }
            var comfyQueueStatus = objectMapper.readValue(response.readEntity(String.class), ComfyQueueInfo.class);
            var queueRunning = comfyQueueStatus.queueRunning();
            var running = findPromptId(queueRunning);
            currentRunningPromptId = running.isEmpty() ? null : running.getFirst();
            var queuePending = comfyQueueStatus.queuePending();
            var pending = findPromptId(queuePending);
            var result = new HashSet<String>(running.size() + pending.size());
            result.addAll(running);
            result.addAll(pending);
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> findPromptId(JsonNode jsonNode) {
        if (!jsonNode.isArray()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<String>();
        for (var node : jsonNode) {
            result.add(node.get(1).asText());
        }
        return result;
    }

    public Set<String> getFileNamesInQueue(String serverAddress) {
        var comfyQueueStatus = getComfyQueueInfo(serverAddress);
        if (comfyQueueStatus == null) {
            return Collections.emptySet();
        }
        var queueRunning = comfyQueueStatus.queueRunning();
        var running = findInputFileName(queueRunning);
        var queuePending = comfyQueueStatus.queuePending();
        var pending = findInputFileName(queuePending);
        var result = new HashSet<String>(running.size() + pending.size());
        result.addAll(running);
        result.addAll(pending);
        return result;
    }

    private ComfyQueueInfo getComfyQueueInfo(String serverAddress) {
        try (var response = client.target(PROTOCOL + serverAddress + "/queue")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
            if (response.getStatus() != 200) {
                Log.warn("Failed to get queue status. " + response.readEntity(String.class));
                return null;
            }
            return objectMapper.readValue(response.readEntity(String.class), ComfyQueueInfo.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String uploadComfyFile(String serverAddress, String fileName, InputStream fileContent) throws IOException {
        FileAndContent fileAndContent = new FileAndContent(fileName, fileContent);
        checkUploadedFile(serverAddress, fileAndContent);
        return uploadFile(serverAddress, fileAndContent);
    }

    protected String uploadFile(String serverAddress, FileAndContent fileAndContent) throws IOException {
        Response response = null;
        var fileName = fileAndContent.fileName();
        var fileContent = fileAndContent.fileContent();
        try {
            WebTarget target = client.target(PROTOCOL + serverAddress + "/upload/image");
            EntityPart filePart = EntityPart.withName("image")
                .fileName(fileName)
                .content(fileContent)
                .mediaType(MediaType.TEXT_PLAIN_TYPE)
                .build();
            EntityPart overwrite = EntityPart.withName("overwrite")
                .content("true")
                .mediaType(MediaType.TEXT_PLAIN_TYPE)
                .build();
            GenericEntity<ArrayList<EntityPart>> genericEntity =
                new GenericEntity<>(Lists.newArrayList(filePart, overwrite)) {
                };
            Entity<GenericEntity<ArrayList<EntityPart>>> entity =
                Entity.entity(genericEntity, MediaType.MULTIPART_FORM_DATA);
            response = target.request(MediaType.MULTIPART_FORM_DATA).post(entity);
            String str = response.readEntity(String.class);
            Log.infof("ComfyUI-upload-image-response: %d, %s", response.getStatus(), str);
            if (response.getStatus() != 200) {
                throw new ServerErrorException(
                    "Failed to upload video to ComfyUI.",
                    Response.Status.INTERNAL_SERVER_ERROR
                );
            }
            ComfyImage comfyImage = objectMapper.readValue(str, ComfyImage.class);
            Log.infof("Successfully uploaded image to ComfyUI: %s", comfyImage);
            return comfyImage.name();
        } finally {
            if (Objects.nonNull(response)) {
                response.close();
            }
            if (Objects.nonNull(fileContent)) {
                fileContent.close();
            }
        }
    }

    public ComfyPromptResponse submitComfyUiPrompt(ComfyUiTask comfyUiTask, String postJson) {
        return submitComfyUiPrompt(comfyUiTask, postJson, true, null);
    }

    public ComfyPromptResponse submitComfyUiPrompt(
        ComfyUiTask comfyUiTask,
        String postJson,
        MediaConvertInfo mediaConvertInfo
    ) {
        return submitComfyUiPrompt(comfyUiTask, postJson, true, mediaConvertInfo);
    }

    protected ComfyPromptResponse submitComfyUiPrompt(
        ComfyUiTask comfyUiTask,
        String postJson,
        boolean addToOngoingPrompts,
        MediaConvertInfo mediaConvertInfo
    ) {
        Log.debugf("ComfyUI-prompt-request: %s", postJson);
        Response response = null;
        try {
            var comfyIp = comfyUiTask.getServerIp();
            comfyIp = addPortToServerIpIfNeeded(comfyIp);
            var fileNames = getFileNamesInQueue(comfyIp);
            if (fileNames.contains(comfyUiTask.getFileName())) {
                Log.infof(
                    "File %s is already in queue for server %s, will not submit it again.",
                    comfyUiTask.getFileName(),
                    comfyIp
                );
                return null;
            }
            Invocation.Builder builder = client.target(PROTOCOL + comfyIp + "/prompt")
                .request(MediaType.APPLICATION_JSON);
            response = builder.post(Entity.entity(postJson, MediaType.APPLICATION_JSON));
            String str = response.readEntity(String.class);
            Log.infof("ComfyUI-prompt-response: %d, %s, from %s", response.getStatus(), str, comfyIp);
            if (response.getStatus() != 200) {
                Log.warnf(
                    "Failed to push task to ComfyUI: %s, comfyIp: %s, workflow: %s, postJson: %s",
                    str,
                    comfyIp,
                    comfyUiTask.getWorkflow(),
                    postJson
                );
                throw new ServerErrorException(
                    "Failed to push task to ComfyUI.",
                    Response.Status.INTERNAL_SERVER_ERROR
                );
            }
            ComfyPromptResponse comfyPromptResponse = objectMapper.readValue(str, ComfyPromptResponse.class);
            if (addToOngoingPrompts) {
                putIntoOngoingPrompts(comfyUiTask, postJson, comfyPromptResponse, mediaConvertInfo);
            }
            return comfyPromptResponse;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } finally {
            if (Objects.nonNull(response)) {
                response.close();
            }
        }
    }

    public static List<String> findInputFileName(JsonNode jsonNode) {
        List<JsonNode> inputs = jsonNode.findValues("inputs");
        return inputs.stream()
            .map(input -> {
                var video = input.findValue("video");
                var image = input.findValue("image");
                if (video != null) {
                    var text = video.asText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                } else if (image != null) {
                    var text = image.asText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
                return null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    protected InputStream readFileFromS3(S3BucketAndKey s3BucketAndKey) {
        String realS3Key;
        if (hasSupportedExtension(s3BucketAndKey.s3Key())) {
            realS3Key = s3BucketAndKey.s3Key();
        } else {
            realS3Key = s3BucketAndKey.s3Key() + "/video_raw.mp4";
        }
        ResponseBytes<GetObjectResponse> responseBytes = s3.getObject(
            builder -> builder.bucket(s3BucketAndKey.s3Bucket()).key(realS3Key),
            ResponseTransformer.toBytes()
        );
        return responseBytes.asInputStream();
    }

    private static boolean hasSupportedExtension(String s3Key) {
        var s3KeyLower = s3Key.toLowerCase();
        return s3KeyLower.endsWith(".mp4") || s3KeyLower.endsWith(".jpg") || s3KeyLower.endsWith(".jpeg")
            || s3KeyLower.endsWith(".png") || s3KeyLower.endsWith(".webp") || s3KeyLower.endsWith(".mp3");
    }

    private static boolean invalidS3Key(ComfyUiWorkflow comfyUiWorkflow, String s3Key, String partialName) {
        if (comfyUiWorkflow != FLUX_PROMPT_WORKFLOW) {
            return s3Key == null || (!s3Key.contains(".") || !s3Key.contains("/"));
        } else {
            return (s3Key == null && partialName == null) || (s3Key != null && !s3Key.contains("."));
        }
    }

    public static String generateFileNameForWorkflow(
        ComfyUiWorkflow comfyUiWorkflow,
        long groupId,
        String s3Key,
        String partialName
    ) {
        if (comfyUiWorkflow == FLUX_PROMPT_WORKFLOW && s3Key == null && partialName == null) {
            return groupId + "-" + FLUX_PROMPT_WORKFLOW.getWorkflowName();
        }
        if (invalidS3Key(comfyUiWorkflow, s3Key, partialName)) {
            throw new BadRequestException("Invalid s3Key: " + s3Key + ", partialName: " + partialName);
        }
        var fileExtension = (s3Key == null || s3Key.isBlank()) ? "" : s3Key.substring(s3Key.lastIndexOf(".") + 1);
        partialName = partialName == null || partialName.isBlank() ? "" : partialName;
        String photoFileName = generatePhotoFileName(s3Key, partialName);
        String workflowName = comfyUiWorkflow.getWorkflowName();
        String result;
        if (fileExtension.isBlank()) {
            result = String.format("%d-%s-%s", groupId, workflowName, photoFileName);
        } else {
            result = String.format("%d-%s-%s.%s", groupId, workflowName, photoFileName, fileExtension);
        }
        Log.debugf(
            "s3Key: %s, PhotoFileName: %s, FileExtension: %s, result: %s",
            s3Key,
            photoFileName,
            fileExtension,
            result
        );
        return result;
    }

    private static String generatePhotoFileName(String s3Key, String partialName) {
        String photoFileName;
        if (s3Key == null || s3Key.isBlank()) {
            photoFileName = partialName;
        } else {
            String substring = s3Key.substring(s3Key.lastIndexOf("/") + 1, s3Key.lastIndexOf("."));
            if (!partialName.isBlank()) {
                photoFileName = substring + "-" + partialName;
            } else {
                photoFileName = substring;
            }
        }
        if (photoFileName.isBlank()) {
            throw new BadRequestException("Invalid s3Key: " + s3Key + ", partialName: " + partialName);
        }
        return photoFileName;
    }

    public List<Flow> getFlows() {
        return CACHE.get("_FLOW", _ -> getFlowList());
    }

    private List<Flow> getFlowList() {
        String flows = readFileFromClasspath("comfyui.json");
        try {
            return objectMapper.readValue(
                flows, new TypeReference<>() {
                }
            );
        } catch (JsonProcessingException e) {
            var message = "Failed to parse comfyui.json";
            Log.errorf(e, message);
            throw new ServerErrorException(message, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void triggerWebSocketReconnectIfTooLong() {
        var now = Instant.now();
        for (var entry : lastSessionConnected.entrySet()) {
            var serverAddr = entry.getKey();
            var lastSessionConnect = entry.getValue();
            var elapsedSecondsSinceLastReconnect = now.getEpochSecond() - lastSessionConnect.getEpochSecond();
            if (elapsedSecondsSinceLastReconnect > SOFT_WEB_SOCKET_RECONNECT_INTERVAL_SECONDS) {
                try {
                    var queueRemaining = comfyUiTaskInstanceSetupManager.comfyUiQueueRemaining(serverAddr);
                    if (queueRemaining == 0 || queueRemaining == -1) {
                        Log.infof(
                            "Queue is empty, but last session connect was %s, reconnecting...",
                            lastSessionConnect
                        );
                        closeWebSocketSession(serverAddr, serverAddrToComfyUiSessions.get(serverAddr));
                    } else if (elapsedSecondsSinceLastReconnect > HARD_WEB_SOCKET_RECONNECT_INTERVAL_SECONDS) {
                        Log.infof(
                            "Queue has %d items, but last session connect was %s, reconnecting...",
                            queueRemaining,
                            lastSessionConnect
                        );
                        closeWebSocketSession(serverAddr, serverAddrToComfyUiSessions.get(serverAddr));
                    }
                } catch (ProcessingException e) {
                    if (e.getCause() instanceof ConnectTimeoutException) {
                        Log.infof(
                            "Connection timeout, will close session: %s, exception message: %s",
                            serverAddr,
                            e.getMessage()
                        );
                        closeWebSocketSession(serverAddr, serverAddrToComfyUiSessions.get(serverAddr));
                    } else {
                        Log.warnf(e, "Failed to get queue remaining for server %s, will close session", serverAddr);
                        closeWebSocketSession(serverAddr, serverAddrToComfyUiSessions.get(serverAddr));
                    }
                }
            }
        }
    }

    protected void checkOngoingPrompts(String serverAddress) {
        var ongoingPromptsForServer = new HashMap<String, OngoingPromptRequest>();
        ongoingPrompts.forEach((promptId, request) -> {
            if (serverAddress.equals(request.comfyIp()) || serverAddress.contains(request.comfyIp())) {
                ongoingPromptsForServer.put(promptId, request);
            }
        });
        Log.infof("Ongoing prompts for server %s: %s", serverAddress, ongoingPromptsForServer.size());
        if (!ongoingPromptsForServer.isEmpty()) {
            var queuedPrompts = getPromptInQueue(serverAddress);
            var addedFileNames = new HashSet<String>();
            var resubmittedPrompts = new HashSet<String>();
            Log.infof("On server: %s, Queued prompts: %s", serverAddress, queuedPrompts);
            Set<String> fileNamesInQueue = getFileNamesInQueue(serverAddress);
            Log.infof("On server: %s, File names in queue: %s", serverAddress, fileNamesInQueue);
            ongoingPromptsForServer.forEach((promptId, ongoingPromptRequest) -> {
                if (!queuedPrompts.contains(promptId)) {
                    Log.infof(
                        "Trying to fetch result for Prompt %s to server: %s, it's not queued.",
                        promptId,
                        serverAddress
                    );
                    var serverIpAndPort = addPortToServerIpIfNeeded(ongoingPromptRequest.comfyIp());
                    grabGeneratedResultAndUpload(
                        serverIpAndPort,
                        promptId,
                        ongoingPromptRequest.partialName(),
                        ongoingPromptRequest.mediaConvertInfo(),
                        ongoingPromptRequest.resultType()
                    );
                }
            });
            ongoingPromptsForServer.forEach((promptId, request) -> {
                if (Instant.now().getEpochSecond() - request.submittedAt().getEpochSecond() >= MAX_PROMPT_WAIT_TIME
                    && !queuedPrompts.contains(promptId)) {
                    Log.warnf(
                        "Prompt %s is ongoing for more than %s minutes, will stop it.",
                        promptId,
                        TimeUnit.MINUTES.convert(MAX_PROMPT_WAIT_TIME, TimeUnit.SECONDS)
                    );
                    removeFromOngoingPrompts(promptId);
                }
            });
            ongoingPromptsForServer.forEach((promptId, request) -> {
                if (queuedPrompts.contains(promptId)) {
                    return;
                }
                var fileName = request.fileName();
                if (fileNamesInQueue.contains(fileName) || fileName == null || fileName.isBlank()
                    || addedFileNames.contains(fileName)) {
                    return;
                }
                Log.infof(
                    "Resubmit the prompt %s to server %s, which was submitted at %s, filename: %s",
                    promptId,
                    request.comfyIp(),
                    request.submittedAt(),
                    fileName
                );
                var comfyUiTask = comfyUiTaskDao.getComfyUiTask(request.comfyUiTaskId());
                var submitted = submitComfyUiPrompt(comfyUiTask, request.postJson(), false, request.mediaConvertInfo());
                if (submitted != null) {
                    addedFileNames.add(fileName);
                    resubmittedPrompts.add(promptId);
                }
            });
            resubmittedPrompts.forEach(ongoingPrompts::remove);
        }
    }

    public String getWorkflow(String flowName) {
        List<Flow> flows = getFlows();
        Flow flow = flows.stream()
            .filter(f -> flowName.equals(f.name()))
            .findFirst()
            .orElse(null);
        if (Objects.isNull(flow)) {
            throw new BadRequestException("None existing workflow: " + flowName);
        }
        return WORKFLOW_CACHE.get(flow.path(), ComfyUiManager::loadWorkflow);
    }

    protected static String loadWorkflow(String flowPath) {
        Log.infof("Loading workflow file: %s", flowPath);
        File file = new File(flowPath);
        if (file.exists()) {
            String json;
            try {
                Log.infof("Loading workflow file from file system: %s", file.toPath());
                json = Files.readString(file.toPath());
            } catch (IOException e) {
                Log.warnf(e, "Failed to load file %s", flowPath);
                throw new ServerErrorException("Failed to load workflow file.", Response.Status.INTERNAL_SERVER_ERROR);
            }
            if (Strings.isNullOrEmpty(json)) {
                throw new BadRequestException("Empty workflow file.");
            }
            return json;
        }
        var result = readFileFromClasspath(flowPath);
        Log.infof("Loading workflow file from classpath: %s", flowPath);
        if (result == null) {
            throw new BadRequestException("Cannot find workflow " + flowPath + ".");
        }
        return result;
    }

    public void handleOngoingPromptHandoverMessage(OngoingPromptHandoverMessage ongoingPromptHandoverMessage) {
        if (!RUNNING) {
            Log.warnf("Not running, skipping handle OngoingPromptHandoverMessage: %s", ongoingPromptHandoverMessage);
            throw new DelayMessageHandlingException(ONGOING_PROMPT_HANDOVER_MESSAGE_DELAY);
        }
        if (Instant.now().getEpochSecond() - ongoingPromptHandoverMessage.submittedAt().getEpochSecond()
            >= MAX_PROMPT_WAIT_TIME) {
            Log.warnf(
                "Prompt %s is ongoing for more than 2 hours, will stop.",
                ongoingPromptHandoverMessage.promptId()
            );
            return;
        }
        Log.infof("Handling OngoingPromptHandoverMessage: %s", ongoingPromptHandoverMessage);
        boolean result = grabGeneratedResultAndUpload(
            ongoingPromptHandoverMessage.serverIpAndPort(),
            ongoingPromptHandoverMessage.promptId(),
            ongoingPromptHandoverMessage.partialName(),
            ongoingPromptHandoverMessage.mediaConvertInfo(),
            ongoingPromptHandoverMessage.resultType()
        );
        if (!result) {
            Log.infof(
                "Failed to get history for prompt %s, will try again after %d second",
                ongoingPromptHandoverMessage.promptId(),
                ONGOING_PROMPT_HANDOVER_MESSAGE_DELAY
            );
            throw new DelayMessageHandlingException(ONGOING_PROMPT_HANDOVER_MESSAGE_DELAY);
        } else {
            Log.infof(
                "Successfully handled handover prompt %s, took %d seconds(including time spent waiting in queue).",
                ongoingPromptHandoverMessage.promptId(),
                Instant.now().getEpochSecond() - ongoingPromptHandoverMessage.submittedAt().getEpochSecond()
            );
        }
    }

    public void dropTasksInSqs() {
        sqs.purgeQueue(builder -> builder.queueUrl(comfyProperties.queueUrl()));
    }

    protected void dropTasksInComfyUi(String serverAddress) {
        try (Response response = client.target(PROTOCOL + serverAddress + "/queue")
            .request()
            .post(Entity.json("{\"clear\":true}"))) {
            if (response.getStatus() != 200) {
                Log.warnf("Failed to drop tasks in ComfyUi, status: %d", response.getStatus());
            }
        }
    }

    public void dropTasksInComfyUiAndInterrupt() {
        String comfyVideoInstanceAddress = comfyProperties.comfyUiLipSyncInstanceAddress();
        dropTasksInComfyUi(comfyVideoInstanceAddress);
        interruptCurrentComfyUiTask(comfyVideoInstanceAddress);
        ongoingPrompts.clear();
        Log.info("Cleared all ongoing prompts.");
    }

    public String showOngoingPrompts() {
        return ongoingPrompts.toString();
    }

    protected void interruptCurrentComfyUiTask(String serverAddress) {
        try (Response response = client.target(PROTOCOL + serverAddress + "/interrupt")
            .request()
            .post(null)) {
            if (response.getStatus() != 200) {
                Log.warnf("Failed to interrupt current task in ComfyUi, status: %d", response.getStatus());
            }
        }
    }

    public void dropAllTasksAndInterrupt() {
        dropTasksInSqs();
        dropTasksInComfyUiAndInterrupt();
    }

    protected void checkUploadedFile(String serverAddress, FileAndContent fileAndContent) {
        var fileName = fileAndContent.fileName();
        if (Objects.isNull(fileName) || Strings.isNullOrEmpty(fileName)) {
            Log.infof("No file uploaded, fileName: %s", fileName);
            throw new BadRequestException("No video or image uploaded.");
        }
        var fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);
        if (!SUPPORTED_FILE_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            Log.infof("Unsupported file extension: %s", fileExtension);
            throw new BadRequestException("Unsupported file extension: " + fileExtension);
        }
        if (fileExistsInQueue(serverAddress, fileName)) {
            Log.infof("file %s is already in queue.", fileName);
            throw new BadRequestException("file '" + fileName + "' is already in the queue.");
        }
        var fileContent = fileAndContent.fileContent();
        if (Objects.isNull(fileContent)) {
            Log.info("No video/image content uploaded.");
            throw new BadRequestException("No video/image content uploaded.");
        }
        Log.infof("Will upload file: %s", fileName);
    }

    protected List<String> uploadFilesAndContents(
        List<InputStream> fileContents,
        List<String> fileNames,
        String serverAddress
    ) throws IOException {
        if (fileContents.size() != fileNames.size()) {
            throw new BadRequestException("File names and file contents size mismatch.");
        }
        var comfyFileNames = new ArrayList<String>(fileContents.size());
        for (int i = 0; i < fileContents.size(); i++) {
            var fileAndContent = new FileAndContent(fileNames.get(i), fileContents.get(i));
            checkUploadedFile(serverAddress, fileAndContent);
            comfyFileNames.add(uploadFile(serverAddress, fileAndContent));
        }
        return comfyFileNames;
    }

    private static String getRealInputS3Key(String inputS3Key) {
        return hasSupportedExtension(inputS3Key) ? inputS3Key : inputS3Key + "/video_raw.mp4";
    }

    private InputStream[] readFilesFromS3(ComfyUiInputAndAuxiliaryFileInS3 message) {
        String realInputS3Key = getRealInputS3Key(message.inputS3Key());
        ResponseBytes<GetObjectResponse> responseBytes = s3.getObject(
            builder -> builder.bucket(message.s3Bucket()).key(realInputS3Key),
            ResponseTransformer.toBytes()
        );
        var inputStream = responseBytes.asInputStream();
        if (message.auxiliaryFileS3Key() == null || message.auxiliaryFileS3Key().isBlank()) {
            return new InputStream[]{inputStream};
        }
        responseBytes = s3.getObject(
            builder -> builder.bucket(message.s3BucketForAuxiliaryFile()).key(message.auxiliaryFileS3Key()),
            ResponseTransformer.toBytes()
        );
        var auxiliaryFileInputStream = responseBytes.asInputStream();
        return new InputStream[]{inputStream, auxiliaryFileInputStream};
    }

    public void handleComfyUiLatentSyncMessage(ComfyUiLatentSyncMessage message) {
        try {
            String serverAddress = getServerAddressForTaskWorkflow(VIDEO_LATENT_SYNC_WORKFLOW);
            String workflowName = VIDEO_LATENT_SYNC_WORKFLOW.getWorkflowName();
            InputStream[] fileContents = readFilesFromS3(message);
            String[] fileNames = generateFileNameForLatentSyncRequest(message);
            var videoFileAndContent = new FileAndContent(fileNames[0], fileContents[0]);
            checkUploadedFile(serverAddress, videoFileAndContent);
            var videoFile = uploadFile(serverAddress, videoFileAndContent);
            var audioFileAndContent = new FileAndContent(fileNames[1], fileContents[1]);
            checkUploadedFile(serverAddress, audioFileAndContent);
            var audioFile = uploadFile(serverAddress, audioFileAndContent);
            var videoAndAudio = new String[]{videoFile, audioFile};
            String json = getWorkflow(workflowName);
            String postJson = generateComfyUiLatentSync(
                json,
                videoAndAudio,
                getClientId(),
                message.startTimeSeconds(),
                message.resultVideoDurationSeconds()
            );
            var comfyUiTask = ComfyUiTask.createVideoTask(
                VIDEO_LATENT_SYNC_WORKFLOW,
                serverAddress,
                videoFile,
                message.partialName(),
                message.videoDurationSeconds(),
                message.groupId(),
                message.levelId(),
                message.videoId(),
                message.version()
            );
            submitComfyUiPrompt(comfyUiTask, postJson, message.mediaConvertInfo());
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load video or audio from S3 bucket: %s, videoKey: %s, audioKey: %s",
                message.s3Bucket(),
                message.inputS3Key(),
                message.audioS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(e, "Failed to handle message: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    private static String[] generateFileNameForLatentSyncRequest(ComfyUiLatentSyncMessage comfyUiLatentSyncMessage) {
        var groupId = comfyUiLatentSyncMessage.groupId();
        var videoFileName = generateFileNameForWorkflow(
            VIDEO_LATENT_SYNC_WORKFLOW,
            groupId,
            comfyUiLatentSyncMessage.inputS3Key(),
            comfyUiLatentSyncMessage.partialName()
        );
        var audioS3Key = comfyUiLatentSyncMessage.audioS3Key();
        var audioFileExtension = audioS3Key.substring(audioS3Key.lastIndexOf("."));
        var audioFileName =
            videoFileName.subSequence(0, videoFileName.lastIndexOf(".")) + "-audio" + audioFileExtension;
        return new String[]{videoFileName, audioFileName};
    }

    private List<InputStream> readFilesFromS3(ComfyUiMultiPhotosAndPromptMessage comfyUiMultiPhotosAndPromptMessage) {
        var inputS3Key = comfyUiMultiPhotosAndPromptMessage.inputS3Key();
        var fileExtension = inputS3Key.substring(inputS3Key.lastIndexOf(".") + 1);
        if (!SUPPORTED_FILE_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            Log.infof("Unsupported file extension: %s", inputS3Key);
            throw new BadRequestException("Unsupported file extension: " + inputS3Key);
        }
        var inputStreams = new ArrayList<InputStream>();
        ResponseBytes<GetObjectResponse> responseBytes = s3.getObject(
            builder -> builder.bucket(comfyUiMultiPhotosAndPromptMessage.s3Bucket()).key(inputS3Key),
            ResponseTransformer.toBytes()
        );
        var videoInputStream = responseBytes.asInputStream();
        inputStreams.add(videoInputStream);
        for (var imageS3Key : comfyUiMultiPhotosAndPromptMessage.sourceS3Keys()) {
            responseBytes = s3.getObject(
                builder -> builder.bucket(comfyUiMultiPhotosAndPromptMessage.getS3BucketForSource()).key(imageS3Key),
                ResponseTransformer.toBytes()
            );
            var imageInputStream = responseBytes.asInputStream();
            inputStreams.add(imageInputStream);
        }
        return inputStreams;
    }

    public ComfyUiMultiResultResponse resultPhotosInfoFromS3(ComfyUiPhotoResultRequest request) {
        Log.infof("Photo-Multi-Result-Request: %s", request);
        ComfyUiWorkflow workflow = ComfyUiWorkflow.fromWorkflowName(request.photoWorkflow());
        if (workflow == null) {
            return new ComfyUiMultiResultResponse(
                resultS3Bucket,
                "Specified photo work flow: %s does not exist.".formatted(request.photoWorkflow())
            );
        }
        if (!PHOTO_WORKFLOWS_WITH_MULTIPLE_RESULTS.contains(workflow) && !PHOTO_WORKFLOWS_WITH_MASK_RESULT.contains(
            workflow)) {
            return new ComfyUiMultiResultResponse(
                resultS3Bucket,
                "Specified photo work flow: %s does not support multiple results.".formatted(request.photoWorkflow())
            );
        }
        var fileNameWithSuffix =
            generateFileNameForWorkflow(workflow, request.groupId(), request.inputS3Key(), request.partialName());
        String fileName;
        if (fileNameWithSuffix.contains(".")) {
            fileName = fileNameWithSuffix.substring(0, fileNameWithSuffix.lastIndexOf("."));
        } else {
            fileName = fileNameWithSuffix;
        }
        var mainResult = getMaxByS3KeyPrefix(RESULT_S3_KEY_PREFIX + "/" + fileName + THUMBNAIL_MAIN_FORMAT);
        if (mainResult.isEmpty()) {
            return comfyUiMultiResultResponseNotFound;
        }
        var coverResult = getMaxByS3KeyPrefix(RESULT_S3_KEY_PREFIX + "/" + fileName + THUMBNAIL_COVER_FORMAT);
        if (coverResult.isEmpty()) {
            return comfyUiMultiResultResponseNotFound;
        }
        var thumbnailResult = getMaxByS3KeyPrefix(RESULT_S3_KEY_PREFIX + "/" + fileName + THUMBNAIL_THUMBNAIL_FORMAT);
        if (thumbnailResult.isEmpty()) {
            return comfyUiMultiResultResponseNotFound;
        }
        Optional<S3Object> maskResult = Optional.empty();
        if (PHOTO_WORKFLOWS_WITH_MASK_RESULT.contains(workflow)) {
            maskResult = getMaxByS3KeyPrefix(RESULT_S3_KEY_PREFIX + "/" + fileName + MASK_FORMAT);
            if (maskResult.isEmpty()) {
                return comfyUiMultiResultResponseNotFound;
            }
        }
        var mainKey = mainResult.get().key();
        var coverKey = coverResult.get().key();
        var thumbnailKey = thumbnailResult.get().key();
        var maskKey = maskResult.map(S3Object::key).orElse(null);
        return new ComfyUiMultiResultResponse(resultS3Bucket, mainKey, coverKey, thumbnailKey, maskKey);
    }

    private Optional<S3Object> getMaxByS3KeyPrefix(String s3KeyPrefix) {
        return s3.listObjects(builder -> builder.bucket(resultS3Bucket).prefix(s3KeyPrefix)).contents().stream()
            .filter(s3Object -> {
                var key = s3Object.key();
                return key.endsWith(".png") || key.endsWith(".jpg") || key.endsWith(".jpeg") || key.endsWith(".mp4");
            })
            .max(Comparator.comparing(S3Object::lastModified));
    }

    public ComfyUiResultResponse resultComfyUiResultFromS3(ComfyUiResultRequest request) {
        Log.infof("ComfyUi-Result-Request: %s", request);
        ComfyUiWorkflow workflow = ComfyUiWorkflow.fromWorkflowName(request.workflow());
        if (workflow == null) {
            return new ComfyUiResultResponse(
                resultS3Bucket,
                "Specified Workflow: %s does not exist.".formatted(request.workflow())
            );
        }
        var fileNameWithSuffix =
            generateFileNameForWorkflow(workflow, request.groupId(), request.inputS3Key(), request.partialName());
        String fileName;
        if (fileNameWithSuffix.contains(".")) {
            fileName = fileNameWithSuffix.substring(0, fileNameWithSuffix.lastIndexOf("."));
        } else {
            fileName = fileNameWithSuffix;
        }
        Log.infof("ComfyUi-Result-Request, fileName: %s", fileName);
        Optional<S3Object> result =
            getMaxByS3KeyPrefix(RESULT_S3_KEY_PREFIX + "/" + fileName);
        if (result.isEmpty()) {
            return comfyUiResultResponseNotFound;
        }
        var key = result.get().key();
        Log.infof("ComfyUi-Result-Response, s3 key: %s", key);
        return new ComfyUiResultResponse(resultS3Bucket, key);
    }

    public ComfyUiResultResponse resultPhotoInfoFromS3(ComfyUiPhotoResultRequest request) {
        return resultComfyUiResultFromS3(toComfyUiResultRequest(request));
    }

    protected ComfyPromptResponse sendCacheComponentsRequest(
        String serverAddress,
        ComfyUiTaskInstanceSetup instanceSetup
    ) {
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress is null or blank: " + serverAddress);
        }
        var workflow = instanceSetup.getCacheComponentsWorkflow().getWorkflowName();
        var json = getWorkflow(workflow);
        var postJson = generatePlainComfyUiRequest(json, getClientId());
        var comfyUiTask = ComfyUiTask.createCacheComponentsTask(serverAddress);
        return submitComfyUiPrompt(comfyUiTask, postJson);
    }

    public ComfyPromptResponse cacheComponents(String serverAddress, ComfyUiTaskInstanceSetup instanceSetup) {
        return cacheComponents(serverAddress, instanceSetup, false);
    }

    public ComfyPromptResponse cacheComponents(
        String serverAddress,
        ComfyUiTaskInstanceSetup instanceSetup,
        boolean sync
    ) {
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress is null or blank: " + serverAddress);
        }
        if (!isComponentsCachedSync(serverAddress, instanceSetup)) {
            if (!sync) {
                return sendCacheComponentsRequest(serverAddress, instanceSetup);
            } else {
                var response = sendCacheComponentsRequest(serverAddress, instanceSetup);
                var promptId = response.promptId();
                var success = waitForPromptDone(promptId, 240);
                if (!success) {
                    Log.warnf(
                        "Failed to cache components in 180 seconds, promptId: %s, serverAddress: %s",
                        promptId,
                        serverAddress
                    );
                }
                return new ComfyPromptResponse(promptId, "0", "cache-components waited too long");
            }
        }
        return new ComfyPromptResponse("already-cached", "0", "{}");
    }

    protected ComfyPromptResponse sendIsComponentsCachedRequest(
        String serverAddress,
        ComfyUiTaskInstanceSetup instanceSetup
    ) {
        // we only check "pipe1_flux1-dev" in the workflow, as it's the biggest one (23.8GB)
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress is null or blank: " + serverAddress);
        }
        var workflow = instanceSetup.getCheckCacheWorkflow().getWorkflowName();
        var json = getWorkflow(workflow);
        var postJson = generatePlainComfyUiRequest(json, getClientId());
        var comfyUiTask = ComfyUiTask.createCheckCacheTask(serverAddress);
        return submitComfyUiPrompt(comfyUiTask, postJson);
    }

    protected boolean isComponentsCachedFast(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress is null or blank: " + serverAddress);
        }
        return componentsCached.getOrDefault(serverAddress, false);
    }

    public boolean isComponentsCachedSync(String serverAddress, ComfyUiTaskInstanceSetup instanceSetup) {
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress is null or blank: " + serverAddress);
        }
        if (isCacheComponentsWorkflowRunning(serverAddress)) {
            return false;
        }
        var response = sendIsComponentsCachedRequest(serverAddress, instanceSetup);
        var promptId = response.promptId();
        var countedDown = waitForPromptDone(promptId, 15);
        return countedDown && isComponentsCachedFast(serverAddress);
    }

    private void releaseWaitingPrompt(String promptId) {
        var countDownLatch = pendingResult.remove(promptId);
        if (countDownLatch != null) {
            countDownLatch.countDown();
        }
    }

    private boolean waitForPromptDone(String promptId, long timeOutSeconds) {
        var countDownLatch = new CountDownLatch(1);
        pendingResult.put(promptId, countDownLatch);
        var countedDown = false;
        try {
            countedDown = countDownLatch.await(timeOutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.warnf("Interrupted while waiting for prompt %s to finish.", promptId);
        }
        return countedDown;
    }

    public boolean isCacheComponentsWorkflowRunning(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalArgumentException("serverAddress is null or blank: " + serverAddress);
        }
        var hasDbRecord = comfyUiTaskDao.isCacheComponentsWorkflowRunning(normalizeServerIp(serverAddress));
        if (!hasDbRecord) {
            return false;
        }
        var comfyQueueInfo = getComfyQueueInfo(serverAddress);
        if (comfyQueueInfo == null) {
            return false;
        }
        var jsonNode = comfyQueueInfo.queueRunning().findValues(COMFY_CLASS_TYPE);
        Set<String> runningClassTypes = jsonNode.stream().map(JsonNode::asText).collect(Collectors.toSet());
        jsonNode = comfyQueueInfo.queuePending().findValues(COMFY_CLASS_TYPE);
        Set<String> pendingClassTypes = jsonNode.stream().map(JsonNode::asText).collect(Collectors.toSet());
        return runningClassTypes.contains(CACHE_BACKEND_DATA_CLASS_TYPE) || pendingClassTypes.contains(
            CACHE_BACKEND_DATA_CLASS_TYPE);
    }

    public void handleComfyUiFluxPhotoPromptMessage(ComfyUiPhotoAndPromptMessage message) {
        try {
            final String comfyIp = getServerAddressForTaskWorkflow(FLUX_PHOTO_PROMPT_WORKFLOW);
            cacheComponents(comfyIp, FLUX_PHOTO_PROMPT_WORKFLOW.getInstanceSetup(), true);
            InputStream fileContent = readFileFromS3(message);
            String fileName = generateFileNameForWorkflow(
                FLUX_PHOTO_PROMPT_WORKFLOW,
                message.groupId(),
                message.s3Key(),
                message.partialName()
            );
            final String image = uploadComfyFile(comfyIp, fileName, fileContent);
            String json = getWorkflow(FLUX_PHOTO_PROMPT_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiPhotoFlux(json, image, message.promptText(), getClientId());
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                FLUX_PHOTO_PROMPT_WORKFLOW,
                comfyIp,
                fileName,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load video from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.s3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(e, "Failed to handle ComfyUiFluxPhotoPromptMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiFluxPromptMessage(ComfyUiPhotoAndPromptMessage message) {
        try {
            String comfyIp = getServerAddressForTaskWorkflow(FLUX_PROMPT_WORKFLOW);
            cacheComponents(comfyIp, FLUX_PROMPT_WORKFLOW.getInstanceSetup(), true);
            String fileName = generateFileNameForWorkflow(
                FLUX_PROMPT_WORKFLOW,
                message.groupId(),
                message.s3Key(),
                message.partialName()
            );
            String json = getWorkflow(FLUX_PROMPT_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiPhotoFlux(json, fileName, message.promptText(), getClientId());
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                FLUX_PROMPT_WORKFLOW,
                comfyIp,
                fileName,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (BadRequestException e) {
            Log.infof(e, "Failed to handle ComfyUiFluxPromptMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    private static List<String> generateFileNameForMultiPhotos(
        ComfyUiMultiPhotosAndPromptMessage message,
        ComfyUiWorkflow workflow
    ) {
        var groupId = message.groupId();
        var fileNames = new ArrayList<String>();
        var inputS3Key = message.inputS3Key();
        var inputFileName = generateFileNameForWorkflow(workflow, groupId, inputS3Key, message.partialName());
        fileNames.add(inputFileName);
        var sourceS3Keys = message.sourceS3Keys();
        for (int i = 0; i < sourceS3Keys.size(); i++) {
            var sourceS3Key = sourceS3Keys.get(i);
            var sourceFileExtension = sourceS3Key.substring(sourceS3Key.lastIndexOf("."));
            var sourceFileName =
                inputFileName.subSequence(0, inputFileName.lastIndexOf(".")) + "-" + workflow.getWorkflowName()
                    + "-source-" + i + sourceFileExtension;
            fileNames.add(sourceFileName);
        }
        return fileNames;
    }

    public void handleComfyUiFluxPhotoReduxStyleMessage(ComfyUiMultiPhotosAndPromptMessage message) {
        try {
            var comfyIp = getServerAddressForTaskWorkflow(FLUX_PHOTO_REDUX_STYLE_WORKFLOW);
            cacheComponents(comfyIp, FLUX_PHOTO_REDUX_STYLE_WORKFLOW.getInstanceSetup(), true);
            var fileContents = readFilesFromS3(message);
            var fileNames = generateFileNameForMultiPhotos(message, FLUX_PHOTO_REDUX_STYLE_WORKFLOW);
            var images = uploadFilesAndContents(fileContents, fileNames, comfyIp);
            String json = getWorkflow(FLUX_PHOTO_REDUX_STYLE_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiPhotoFluxReduxStyle(
                json,
                images.getFirst(),
                images.get(1),
                message.promptText(),
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                FLUX_PHOTO_REDUX_STYLE_WORKFLOW,
                comfyIp,
                fileNames.getFirst(),
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(e, "Failed to handle ComfyUiFluxPhotoPromptMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiPhotoPulidMultiCharMessage(ComfyUiMultiPhotosAndPromptMessage message) {
        var workflow = ComfyUiWorkflow.fromWorkflowName(getMultiCharWorkflowForPulid(
            "photo-pulid",
            message.sourceS3Keys().size() + 1
        ));
        if (workflow == null) {
            String errorMessage = "Wrong workflow for ComfyUiPhotoPulidMultiCharMessage: " + message;
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), errorMessage);
            Log.infof("Failed to handle ComfyUiPhotoPulidMultiCharMessage: %s, will ignore it.", message);
            return;
        }
        try {
            String comfyIp = getServerAddressForTaskWorkflow(workflow);
            var fileContents = readFilesFromS3(message);
            var fileNames = generateFileNameForMultiPhotos(message, workflow);
            if (fileContents.size() != fileNames.size()) {
                Log.infof(
                    "Failed to load images from S3 bucket: %s, sourceS3Bucket: %s, inputS3Key: %s, sourceS3Keys: %s",
                    message.s3Bucket(),
                    message.sourceS3Bucket(),
                    message.inputS3Key(),
                    message.sourceS3Keys()
                );
                throw new BadRequestException("Failed to load images from S3 bucket.");
            }
            var comfyFileNames = uploadFilesAndContents(fileContents, fileNames, comfyIp);
            Log.infof("Pulid multi-char workflow: %s", workflow);
            String json = getWorkflow(workflow.getWorkflowName());
            String postJson = generateComfyUiPhotoPulidMultiChars(
                json,
                comfyFileNames.toArray(new String[0]),
                message.promptText(),
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                workflow,
                comfyIp,
                fileNames.getFirst(),
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, sourceS3Bucket: %s, inputS3Key: %s, sourceS3Keys: %s",
                message.s3Bucket(),
                message.sourceS3Bucket(),
                message.inputS3Key(),
                message.sourceS3Keys()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(
                e,
                "Failed to handle ComfyUiPhotoPulidMultiCharMessage: %s, will ignore it.",
                message
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        } catch (NoSuchKeyException e) {
            Log.infof(
                e,
                "Failed to handle ComfyUiPhotoPulidMultiCharMessage: %s, because some keys do not exist in S3, ignore.",
                message
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        }
    }

    public void handleComfyUiPhotoMakeUpThumbnailFamilies(
        ComfyUiMultiPhotosAndPromptMessage message,
        ComfyUiWorkflow workflow
    ) {
        if (!isPhotoMakeUpThumbnailFamilies(workflow)) {
            String errorMessage = "Invalid workflow for ComfyUiPhotoMakeUpThumbnailFamilies: " + workflow;
            Log.info(errorMessage);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), errorMessage);
            return;
        }
        try {
            var comfyIp = getServerAddressForTaskWorkflow(workflow);
            cacheComponents(comfyIp, workflow.getInstanceSetup(), true);
            var fileContents = readFilesFromS3(message);
            var fileNames = generateFileNameForMultiPhotos(message, workflow);
            var images = uploadFilesAndContents(fileContents, fileNames, comfyIp);
            String json = getWorkflow(workflow.getWorkflowName());
            String postJson = generateComfyUiPhotoMakeup(json, images.toArray(new String[0]), getClientId());
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                workflow,
                comfyIp,
                fileNames.getFirst(),
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(e, "Failed to handle ComfyUiPhotoMakeUpThumbnailFamilies: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiPhotoMakeUpThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage message) {
        handleComfyUiPhotoMakeUpThumbnailFamilies(message, PHOTO_MAKE_UP_THUMBNAILS_WORKFLOW);
    }

    public void handleComfyUiPhotoMakeUpEyebrowsThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage message) {
        handleComfyUiPhotoMakeUpThumbnailFamilies(message, PHOTO_MAKE_UP_EYEBROWS_THUMBNAILS_WORKFLOW);
    }

    public void handleComfyUiPhotoMakeUpEyelashesEyeshadowThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage message) {
        handleComfyUiPhotoMakeUpThumbnailFamilies(message, PHOTO_MAKE_UP_EYELASHES_EYESHADOW_THUMBNAILS_WORKFLOW);
    }

    public void handleComfyUiPhotoMakeUpLipsThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage message) {
        handleComfyUiPhotoMakeUpThumbnailFamilies(message, PHOTO_MAKE_UP_LIPS_THUMBNAILS_WORKFLOW);
    }

    public void handleComfyUiPhotoMakeUpSkinThumbnailsMessage(ComfyUiMultiPhotosAndPromptMessage message) {
        handleComfyUiPhotoMakeUpThumbnailFamilies(message, PHOTO_MAKE_UP_SKIN_THUMBNAILS_WORKFLOW);
    }

    private void handleComfyUiInputAndAudioMessage(
        ComfyUiInputAndAudioAndPromptMessage message,
        ComfyUiWorkflow workflow,
        boolean hasSeed
    ) {
        try {
            var comfyIp = getServerAddressForTaskWorkflow(workflow);
            var fileContents = readFilesFromS3(message);
            var fileNames = generateFileNameForInputAndAudio(message, workflow);
            var uploaded = uploadFilesAndContents(Arrays.stream(fileContents).toList(), fileNames, comfyIp);
            String json = getWorkflow(workflow.getWorkflowName());
            String postJson = generateComfyUiRequestWithInputAndAudio(
                json,
                uploaded,
                getClientId(),
                hasSeed,
                message.audioStartTime(),
                message.audioDuration()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                workflow,
                comfyIp,
                fileNames.getFirst(),
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(
                e,
                "Failed to handle ComfyUiInputAndAudioAndPromptMessage: %s for workflow %s, will ignore it.",
                message,
                workflow.getWorkflowName()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiSonicAudioMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        handleComfyUiInputAndAudioMessage(message, SONIC_AUDIO_WORKFLOW, true);
    }

    private static List<String> generateFileNameForInputAndAudio(
        ComfyUiInputAndAuxiliaryFileInS3 message,
        ComfyUiWorkflow workflow
    ) {
        var groupId = message.groupId();
        var fileNames = new ArrayList<String>();
        var inputFileName = generateFileNameForWorkflow(
            workflow,
            groupId,
            getRealInputS3Key(message.inputS3Key()),
            message.partialName()
        );
        fileNames.add(inputFileName);
        var sourceS3Key = message.auxiliaryFileS3Key();
        var sourceFileExtension = sourceS3Key.substring(sourceS3Key.lastIndexOf("."));
        var sourceFileName =
            inputFileName.subSequence(0, inputFileName.lastIndexOf(".")) + "-" + workflow.getWorkflowName()
                + sourceFileExtension;
        fileNames.add(sourceFileName);
        return fileNames;
    }

    public void handleComfyUiSonicTextMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        try {
            var comfyIp = getServerAddressForTaskWorkflow(SONIC_TEXT_WORKFLOW);
            var fileContents = readFilesFromS3(message);
            var fileName = generateFileNameForWorkflow(
                SONIC_TEXT_WORKFLOW,
                message.groupId(),
                message.inputS3Key(),
                message.partialName()
            );
            var uploaded = uploadComfyFile(comfyIp, fileName, fileContents[0]);
            String json = getWorkflow(SONIC_TEXT_WORKFLOW.getWorkflowName());
            var contextValues = message.contextValues();
            ContextSwitchSpeakerMode speakerMode;
            ContextSwitchLanguageMode languageMode;
            if (contextValues == null || contextValues.size() != 2) {
                Log.infof(
                    "%s Invalid context values: %s",
                    VIDEO_ON_OUTPUT_TEXT_WORKFLOW.getWorkflowName(),
                    contextValues
                );
                speakerMode = ContextSwitchSpeakerMode.DEFAULT;
                languageMode = ContextSwitchLanguageMode.DEFAULT;
            } else {
                speakerMode = ContextSwitchSpeakerMode.fromContextValue(contextValues.get(0));
                languageMode = ContextSwitchLanguageMode.fromContextValue(contextValues.get(1));
            }
            String postJson = generateComfyUiRequestWithInputAndPromptTextAndTwoContextValuesAndSeed(
                json,
                uploaded,
                message.promptText(),
                speakerMode,
                languageMode,
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                SONIC_TEXT_WORKFLOW,
                comfyIp,
                uploaded,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(
                e,
                "Failed to handle ComfyUiInputAndAudioAndPromptMessage: %s for workflow %s, will ignore it.",
                message,
                SONIC_TEXT_WORKFLOW.getWorkflowName()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiPhotoAcePlusMessage(ComfyUiPhotoAcePlusMessage message) {
        try {
            AcePlusWardrobeMode wardrobeMode =
                AcePlusWardrobeMode.fromContextValue(message.AcePlusWardrobeModeContextValue());
            AcePlusReferenceMode referenceMode =
                AcePlusReferenceMode.fromContextValue(message.AcePlusReferenceModeContextValue());
            AcePlusMaskMode maskMode = AcePlusMaskMode.fromContextValue(message.AcePlusMaskModeContextValue());
            if (referenceMode == AcePlusReferenceMode.UploadImage && (message.sourceS3Key() == null
                || message.sourceS3Key().isEmpty())) {
                Log.info("ComfyUiPhotoAcePlusMessage: Need to have source image for default AcePlusReferenceMode.");
                throw new BadRequestException("No uploaded source image for default AcePlusReferenceMode.");
            }
            if (referenceMode == AcePlusReferenceMode.Prompt && (message.promptText() == null || message.promptText()
                .isEmpty())) {
                Log.info("ComfyUiPhotoAcePlusMessage: Need to have prompt text for AcePlusReferenceMode.Prompt.");
                throw new BadRequestException("No prompt text for prompt AcePlusReferenceMode.");
            }
            if (referenceMode == AcePlusReferenceMode.Prompt) {
                throw new NotAcceptableException("Prompt AcePlusReferenceMode is NOT supported yet.");
            }
            if (maskMode == AcePlusMaskMode.ManualMask && (message.maskS3Key() == null || message.maskS3Key()
                .isEmpty())) {
                Log.info("ComfyUiPhotoAcePlusMessage: Need to have mask image for ManualMask AcePlusMaskMode.");
                throw new BadRequestException("No mask image for ManualMask AcePlusMaskMode.");
            }
            final String comfyIp = getServerAddressForTaskWorkflow(PHOTO_ACE_PLUS_WORKFLOW);
            cacheComponents(comfyIp, PHOTO_ACE_PLUS_WORKFLOW.getInstanceSetup(), true);
            var fileContents = readFilesFromS3(message, referenceMode, maskMode);
            var fileNames = generateFileNameForPhotoAcePlus(message, referenceMode, maskMode);
            if (fileContents.size() != fileNames.size()) {
                Log.infof(
                    "Failed to load images from S3 bucket: %s, inputS3Key: %s, sourceKey: %s, maskKey: %s",
                    message.s3Bucket(),
                    message.inputS3Key(),
                    message.sourceS3Key(),
                    message.maskS3Key()
                );
                throw new BadRequestException("Failed to load images from S3 bucket.");
            }
            var comfyFileNames = uploadFilesAndContents(fileContents, fileNames, comfyIp);
            String json = getWorkflow(PHOTO_ACE_PLUS_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiPhotoAcePlus(
                json,
                comfyFileNames,
                message.promptText(),
                getClientId(),
                wardrobeMode,
                referenceMode,
                maskMode
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                PHOTO_ACE_PLUS_WORKFLOW,
                comfyIp,
                comfyFileNames.getFirst(),
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, inputS3Key: %s, sourceKey: %s, maskKey: %s",
                message.s3Bucket(),
                message.inputS3Key(),
                message.sourceS3Key(),
                message.maskS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (ClientErrorException e) {
            Log.infof(e, "Failed to handle ComfyUiPhotoAcePlusMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    private List<String> generateFileNameForPhotoAcePlus(
        ComfyUiPhotoAcePlusMessage message,
        AcePlusReferenceMode referenceMode,
        AcePlusMaskMode maskMode
    ) {
        var result = new ArrayList<String>();
        var input = generateFileNameForWorkflow(
            PHOTO_ACE_PLUS_WORKFLOW,
            message.groupId(),
            message.inputS3Key(),
            message.partialName()
        );
        result.add(input);
        if (referenceMode == AcePlusReferenceMode.UploadImage) {
            var sourceS3Key = message.sourceS3Key();
            var sourceFileExtension = sourceS3Key.substring(sourceS3Key.lastIndexOf("."));
            var source = input.subSequence(0, input.lastIndexOf(".")) + "-" + PHOTO_ACE_PLUS_WORKFLOW.getWorkflowName()
                + "-source-" + message.partialName() + "-" + sourceFileExtension;
            result.add(source);
        }
        if (maskMode == AcePlusMaskMode.ManualMask) {
            var maskS3Key = message.maskS3Key();
            var maskFileExtension = maskS3Key.substring(maskS3Key.lastIndexOf("."));
            var mask = input.subSequence(0, input.lastIndexOf(".")) + "-" + PHOTO_ACE_PLUS_WORKFLOW.getWorkflowName()
                + "-mask-" + message.partialName() + "-" + maskFileExtension;
            result.add(mask);
        }
        return result;
    }

    private List<InputStream> readFilesFromS3(
        ComfyUiPhotoAcePlusMessage message,
        AcePlusReferenceMode referenceMode,
        AcePlusMaskMode maskMode
    ) {
        var result = new ArrayList<InputStream>();
        var input = readFileFromS3(S3BucketAndKey.create(message.s3Bucket(), message.inputS3Key()));
        result.add(input);
        if (referenceMode == AcePlusReferenceMode.UploadImage) {
            var source = readFileFromS3(S3BucketAndKey.create(message.getS3BucketForSource(), message.sourceS3Key()));
            result.add(source);
        }
        if (maskMode == AcePlusMaskMode.ManualMask) {
            var mask = readFileFromS3(S3BucketAndKey.create(message.getS3BucketForMask(), message.maskS3Key()));
            result.add(mask);
        }
        return result;
    }

    public void handleComfyUiVideoOnOutputAudioMessage(ComfyUiVideoAndAudioAndPromptMessage message) {
        try {
            var comfyIp = getServerAddressForTaskWorkflow(VIDEO_ON_OUTPUT_AUDIO_WORKFLOW);
            var fileContents = readFilesFromS3(message);
            var fileNames = generateFileNameForInputAndAudio(message, VIDEO_ON_OUTPUT_AUDIO_WORKFLOW);
            var uploaded = uploadFilesAndContents(Arrays.stream(fileContents).toList(), fileNames, comfyIp);
            String json = getWorkflow(VIDEO_ON_OUTPUT_AUDIO_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiRequestWithInputAndAudio(
                json,
                uploaded,
                getClientId(),
                message.audioStartTime(),
                message.audioDuration()
            );
            var comfyUiTask = ComfyUiTask.createVideoTask(
                SONIC_AUDIO_WORKFLOW,
                comfyIp,
                fileNames.getFirst(),
                message.partialName(),
                message.videoDurationSeconds(),
                message.groupId(),
                message.videoId(),
                message.levelId(),
                message.version()
            );
            submitComfyUiPrompt(comfyUiTask, postJson, message.mediaConvertInfo());
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(e, "Failed to handle ComfyUiVideoOnOutputAudioMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiVideoOnOutputTextMessage(ComfyUiVideoAndAudioAndPromptMessage message) {
        try {
            var comfyIp = getServerAddressForTaskWorkflow(VIDEO_ON_OUTPUT_TEXT_WORKFLOW);
            var fileContents = readFilesFromS3(message);
            var fileName = generateFileNameForWorkflow(
                VIDEO_ON_OUTPUT_TEXT_WORKFLOW,
                message.groupId(),
                message.inputS3Key(),
                message.partialName()
            );
            var uploaded = uploadComfyFile(comfyIp, fileName, fileContents[0]);
            String json = getWorkflow(VIDEO_ON_OUTPUT_TEXT_WORKFLOW.getWorkflowName());
            var contextValues = message.contextValues();
            ContextSwitchSpeakerMode speakerMode;
            ContextSwitchLanguageMode languageMode;
            if (contextValues == null || contextValues.size() != 2) {
                Log.infof(
                    "%s Invalid context values: %s",
                    VIDEO_ON_OUTPUT_TEXT_WORKFLOW.getWorkflowName(),
                    contextValues
                );
                speakerMode = ContextSwitchSpeakerMode.DEFAULT;
                languageMode = ContextSwitchLanguageMode.DEFAULT;
            } else {
                speakerMode = ContextSwitchSpeakerMode.fromContextValue(contextValues.get(0));
                languageMode = ContextSwitchLanguageMode.fromContextValue(contextValues.get(1));
            }
            String postJson = generateComfyUiRequestWithInputAndPromptTextAndTwoContextValues(
                json,
                uploaded,
                message.promptText(),
                speakerMode,
                languageMode,
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                VIDEO_ON_OUTPUT_TEXT_WORKFLOW,
                comfyIp,
                uploaded,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson, message.mediaConvertInfo());
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(
                e,
                "Failed to handle ComfyUiVideoAndAudioAndPromptMessage: %s for workflow %s, will ignore it.",
                message,
                VIDEO_ON_OUTPUT_TEXT_WORKFLOW.getWorkflowName()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiLatentSyncTextMessage(ComfyUiVideoAndAudioAndPromptMessage message) {
        try {
            var comfyIp = getServerAddressForTaskWorkflow(VIDEO_LATENT_SYNC_TEXT_WORKFLOW);
            var fileContents = readFilesFromS3(message);
            var fileName = generateFileNameForWorkflow(
                VIDEO_LATENT_SYNC_TEXT_WORKFLOW,
                message.groupId(),
                message.inputS3Key(),
                message.partialName()
            );
            var uploaded = uploadComfyFile(comfyIp, fileName, fileContents[0]);
            String json = getWorkflow(VIDEO_LATENT_SYNC_TEXT_WORKFLOW.getWorkflowName());
            var contextValues = message.contextValues();
            ContextSwitchSpeakerMode speakerMode;
            ContextSwitchLanguageMode languageMode;
            if (contextValues == null || contextValues.size() != 2) {
                Log.infof(
                    "%s Invalid context values: %s",
                    VIDEO_ON_OUTPUT_TEXT_WORKFLOW.getWorkflowName(),
                    contextValues
                );
                speakerMode = ContextSwitchSpeakerMode.DEFAULT;
                languageMode = ContextSwitchLanguageMode.DEFAULT;
            } else {
                speakerMode = ContextSwitchSpeakerMode.fromContextValue(contextValues.get(0));
                languageMode = ContextSwitchLanguageMode.fromContextValue(contextValues.get(1));
            }
            String postJson = generateComfyUiRequestWithInputAndPromptTextAndTwoContextValuesAndSeed(
                json,
                uploaded,
                message.promptText(),
                speakerMode,
                languageMode,
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                VIDEO_LATENT_SYNC_TEXT_WORKFLOW,
                comfyIp,
                uploaded,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson, message.mediaConvertInfo());
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(
                e,
                "Failed to handle ComfyUiVideoAndAudioAndPromptMessage: %s for workflow %s, will ignore it.",
                message,
                VIDEO_LATENT_SYNC_TEXT_WORKFLOW.getWorkflowName()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiStillImageTextMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        try {
            var comfyIp = getServerAddressForTaskWorkflow(STILL_IMAGE_TEXT_WORKFLOW);
            var fileContents = readFilesFromS3(message);
            var contextValues = message.contextValues();
            ContextSwitchSpeakerMode speakerMode;
            ContextSwitchLanguageMode languageMode;
            if (contextValues == null || contextValues.size() != 2) {
                Log.infof("Invalid context values: %s", contextValues);
                speakerMode = ContextSwitchSpeakerMode.DEFAULT;
                languageMode = ContextSwitchLanguageMode.DEFAULT;
            } else {
                speakerMode = ContextSwitchSpeakerMode.fromContextValue(contextValues.get(0));
                languageMode = ContextSwitchLanguageMode.fromContextValue(contextValues.get(1));
            }
            var fileName = generateFileNameForWorkflow(
                STILL_IMAGE_TEXT_WORKFLOW,
                message.groupId(),
                message.inputS3Key(),
                message.partialName()
            );
            var uploaded = uploadComfyFile(comfyIp, fileName, fileContents[0]);
            String json = getWorkflow(STILL_IMAGE_TEXT_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiStillImageText(
                json,
                uploaded,
                message.promptText(),
                speakerMode,
                languageMode,
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                STILL_IMAGE_TEXT_WORKFLOW,
                comfyIp,
                uploaded,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException e) {
            Log.infof(
                e,
                "Failed to handle ComfyUiStillImageTextMessage: %s, will ignore it.",
                message
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiStillImageAudioMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        handleComfyUiInputAndAudioMessage(message, STILL_IMAGE_AUDIO_WORKFLOW, false);
    }

    public void handleComfyUiMusicGenMessage(ComfyUiMusicGenMessage message) {
        try {
            final String comfyIp = getServerAddressForTaskWorkflow(MUSIC_GEN_WORKFLOW);
            InputStream fileContent = readFileFromS3(message);
            String fileName = generateFileNameForWorkflow(
                MUSIC_GEN_WORKFLOW,
                message.groupId(),
                message.s3Key(),
                message.partialName()
            );
            final String video = uploadComfyFile(comfyIp, fileName, fileContent);
            String json = getWorkflow(MUSIC_GEN_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiMusicGen(
                json,
                video,
                message.promptText(),
                MusicGenBackGroundMusic.fromContextValue(message.backGroundMusicContextValue()),
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                MUSIC_GEN_WORKFLOW,
                comfyIp,
                video,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load video from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.s3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException | IllegalArgumentException e) {
            Log.infof(e, "Failed to handle ComfyUiMusicGenMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    public void handleComfyUiMmAudioMessage(ComfyUiInputAndAudioAndPromptMessage message) {
        try {
            final String comfyIp = getServerAddressForTaskWorkflow(MM_AUDIO_WORKFLOW);
            cacheComponents(comfyIp, MM_AUDIO_WORKFLOW.getInstanceSetup(), true);
            var fileContents = readFilesFromS3(message);
            String fileName = generateFileNameForWorkflow(
                MM_AUDIO_WORKFLOW,
                message.groupId(),
                message.inputS3Key(),
                message.partialName()
            );
            final String video = uploadComfyFile(comfyIp, fileName, fileContents[0]);
            String json = getWorkflow(MM_AUDIO_WORKFLOW.getWorkflowName());
            var contextValues = message.contextValues();
            MmAudioAudioMode audioMode;
            MmAudioPromptMode promptMode;
            if (contextValues == null || contextValues.size() != 2) {
                Log.infof("%s Invalid context values: %s", MM_AUDIO_WORKFLOW.getWorkflowName(), contextValues);
                audioMode = MmAudioAudioMode.DEFAULT;
                promptMode = MmAudioPromptMode.DEFAULT;
            } else {
                audioMode = MmAudioAudioMode.fromContextValue(contextValues.get(0));
                promptMode = MmAudioPromptMode.fromContextValue(contextValues.get(1));
            }
            String postJson = generateComfyUiMmAudio(
                json,
                video,
                message.promptText(),
                audioMode,
                promptMode,
                getClientId()
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                MM_AUDIO_WORKFLOW,
                comfyIp,
                video,
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load video from S3 bucket: %s, key: %s",
                message.s3Bucket(),
                message.inputS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (BadRequestException | IllegalArgumentException e) {
            Log.infof(e, "Failed to handle ComfyUiMmAudioMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    private List<InputStream> readFilesFromS3(
        ComfyUiVideoLivePortraitMessage message,
        LivePortraitAudioInputMode audioInputMode
    ) {
        var result = new ArrayList<InputStream>();
        var input = readFileFromS3(S3BucketAndKey.create(message.s3Bucket(), message.inputS3Key()));
        result.add(input);
        if (audioInputMode == LivePortraitAudioInputMode.InputAudio) {
            var sourceAudio = readFileFromS3(S3BucketAndKey.create(
                message.getS3BucketForSourceAudio(),
                message.sourceAudioS3Key()
            ));
            result.add(sourceAudio);
        }
        if (audioInputMode == LivePortraitAudioInputMode.InputVideoDrivingAudio) {
            var sourceVideo = readFileFromS3(S3BucketAndKey.create(
                message.getS3BucketForSourceVideo(),
                message.sourceVideoS3Key()
            ));
            result.add(sourceVideo);
        }
        return result;
    }

    public void handleComfyUiLivePortraitMessage(ComfyUiVideoLivePortraitMessage message) {
        try {
            LivePortraitAudioInputMode audioInputMode =
                LivePortraitAudioInputMode.fromContextValue(message.livePortraitAudioInputModeContextValue());
            LivePortraitCopperMode copperMode =
                LivePortraitCopperMode.fromContextValue(message.livePortraitCopperModeContextValue());
            LivePortraitModelMode modelMode =
                LivePortraitModelMode.fromContextValue(message.livePortraitModelModeContextValue());
            if (audioInputMode == LivePortraitAudioInputMode.InputAudio) {
                if (message.sourceAudioS3Key() == null || message.sourceAudioS3Key().isEmpty()) {
                    Log.info("Need to have source audio for InputAudio LivePortraitAudioInputMode.");
                    throw new BadRequestException("No source audio for InputAudio LivePortraitAudioInputMode.");
                }
                if (!message.sourceAudioS3Key().endsWith("mp3")) {
                    Log.info("Source audio must be in mp3 format.");
                    throw new BadRequestException("Source audio must be in mp3 format.");
                }
            }
            if (audioInputMode == LivePortraitAudioInputMode.InputVideoDrivingAudio && (
                message.sourceVideoS3Key() == null || message.sourceVideoS3Key().isEmpty())) {
                Log.info("Need to have source video for InputVideoDrivingAudio LivePortraitAudioInputMode.");
                throw new BadRequestException("No source video for InputVideoDrivingAudio LivePortraitAudioInputMode.");
            }
            final String comfyIp = getServerAddressForTaskWorkflow(VIDEO_LIVE_PORTRAIT_WORKFLOW);
            cacheComponents(comfyIp, VIDEO_LIVE_PORTRAIT_WORKFLOW.getInstanceSetup(), true);
            var fileContents = readFilesFromS3(message, audioInputMode);
            var fileNames = generateFileNameForVideoLivePortrait(message, audioInputMode);
            if (fileContents.size() != fileNames.size()) {
                Log.infof(
                    "Failed to load files from S3 bucket: %s, inputS3Key: %s, sourceAudioKey: %s, sourceVideoKey: %s",
                    message.s3Bucket(),
                    message.inputS3Key(),
                    message.sourceAudioS3Key(),
                    message.sourceVideoS3Key()
                );
                throw new BadRequestException("Failed to load files from S3 bucket.");
            }
            var comfyFileNames = uploadFilesAndContents(fileContents, fileNames, comfyIp);
            String json = getWorkflow(VIDEO_LIVE_PORTRAIT_WORKFLOW.getWorkflowName());
            String postJson = generateComfyUiVideoLivePortrait(
                json,
                comfyFileNames,
                getClientId(),
                message.sourceAudioStartTime(),
                message.sourceAudioDuration(),
                audioInputMode,
                copperMode,
                modelMode
            );
            var comfyUiTask = ComfyUiTask.createTaskForWorkflow(
                VIDEO_LIVE_PORTRAIT_WORKFLOW,
                comfyIp,
                comfyFileNames.getFirst(),
                message.partialName(),
                message.groupId()
            );
            submitComfyUiPrompt(comfyUiTask, postJson);
        } catch (IOException | NoSuchKeyException e) {
            Log.errorf(
                e,
                "Failed to load images from S3 bucket: %s, inputS3Key: %s, sourceAudioKey: %s, sourceVideoKey: %s",
                message.s3Bucket(),
                message.inputS3Key(),
                message.sourceAudioS3Key(),
                message.sourceVideoS3Key()
            );
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), "Failed to load video from S3 bucket");
        } catch (ClientErrorException e) {
            Log.infof(e, "Failed to handle ComfyUiVideoLivePortraitMessage: %s, will ignore it.", message);
            comfyUiResponsePublisher.failToGenerateResult(message.partialName(), e.getMessage());
        }
    }

    private List<String> generateFileNameForVideoLivePortrait(
        ComfyUiVideoLivePortraitMessage message,
        LivePortraitAudioInputMode audioInputMode
    ) {
        var result = new ArrayList<String>();
        var input = generateFileNameForWorkflow(
            VIDEO_LIVE_PORTRAIT_WORKFLOW,
            message.groupId(),
            message.inputS3Key(),
            message.partialName()
        );
        result.add(input);
        if (audioInputMode == LivePortraitAudioInputMode.InputAudio) {
            var sourceAudioS3Key = message.sourceAudioS3Key();
            var sourceFileExtension = sourceAudioS3Key.substring(sourceAudioS3Key.lastIndexOf("."));
            var sourceAudio =
                input.subSequence(0, input.lastIndexOf(".")) + "-" + VIDEO_LIVE_PORTRAIT_WORKFLOW.getWorkflowName()
                    + "-sourceAudio-audio-" + message.partialName() + "-" + sourceFileExtension;
            result.add(sourceAudio);
        }
        if (audioInputMode == LivePortraitAudioInputMode.InputVideoDrivingAudio) {
            var sourceVideoS3Key = message.sourceVideoS3Key();
            var sourceFileExtension = sourceVideoS3Key.substring(sourceVideoS3Key.lastIndexOf("."));
            var sourceVideo =
                input.subSequence(0, input.lastIndexOf(".")) + "-" + VIDEO_LIVE_PORTRAIT_WORKFLOW.getWorkflowName()
                    + "-source-video-" + message.partialName() + "-" + sourceFileExtension;
            result.add(sourceVideo);
        }
        return result;
    }
}