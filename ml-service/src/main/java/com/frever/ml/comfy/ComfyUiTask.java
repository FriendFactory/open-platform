package com.frever.ml.comfy;

import com.frever.ml.comfy.dto.ComfyUiWorkflow;
import java.time.Instant;
import java.util.UUID;

public class ComfyUiTask {
    public static final int DEFAULT_DURATION = 15;
    public static final int PHOTO_TASK_DURATION = 2;

    private long id;
    private UUID promptId;
    private String serverIp;
    private long groupId;
    private long videoId;
    private long levelId;
    private String version;
    private String workflow;
    private String fileName;
    private String partialName;
    private int duration;
    private int priority;
    private Instant createdAt;
    private Instant finishedAt;
    private Instant startedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID getPromptId() {
        return promptId;
    }

    public void setPromptId(UUID promptId) {
        this.promptId = promptId;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public long getVideoId() {
        return videoId;
    }

    public void setVideoId(long videoId) {
        this.videoId = videoId;
    }

    public long getLevelId() {
        return levelId;
    }

    public void setLevelId(long levelId) {
        this.levelId = levelId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getWorkflow() {
        return workflow;
    }

    public void setWorkflow(String workflow) {
        this.workflow = workflow;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPartialName() {
        return partialName;
    }

    public void setPartialName(String partialName) {
        this.partialName = partialName;
    }

    public int getDuration() {
        return duration == 0 ? DEFAULT_DURATION : duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public static ComfyUiTask createVideoTask(
        ComfyUiWorkflow comfyUiWorkflow,
        String serverIp,
        String fileName,
        String partialName,
        int duration,
        long groupId,
        long videoId,
        long levelId,
        String version
    ) {
        return createTask(
            comfyUiWorkflow.getWorkflowName(),
            serverIp,
            fileName,
            partialName,
            duration,
            groupId,
            videoId,
            levelId,
            version
        );
    }

    public static ComfyUiTask createTaskForWorkflow(
        ComfyUiWorkflow workflow,
        String serverIp,
        String fileName,
        String partialName,
        long groupId
    ) {
        return createTask(
            workflow.getWorkflowName(),
            serverIp,
            fileName,
            partialName,
            PHOTO_TASK_DURATION,
            groupId,
            -1,
            -1,
            ""
        );
    }

    public static ComfyUiTask createTask(
        String workflow,
        String serverIp,
        String fileName,
        String partialName,
        int duration,
        long groupId,
        long videoId,
        long levelId,
        String version
    ) {
        var task = new ComfyUiTask();
        task.setServerIp(serverIp);
        task.setFileName(fileName);
        task.setPartialName(partialName);
        task.setDuration(duration);
        task.setGroupId(groupId);
        task.setVideoId(videoId);
        task.setLevelId(levelId);
        task.setVersion(version);
        task.setWorkflow(workflow);
        return task;
    }

    public static ComfyUiTask createCheckCacheTask(String serverIp) {
        return createTask("is-cached", serverIp, "", "", 0, -1, -1, -1, "");
    }

    public static ComfyUiTask createCacheComponentsTask(String serverIp) {
        return createTask("cache-components", serverIp, "", "", 90, -1, -1, -1, "");
    }
}
