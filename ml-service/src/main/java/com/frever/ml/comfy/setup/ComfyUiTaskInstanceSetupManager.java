package com.frever.ml.comfy.setup;

import static com.frever.ml.comfy.ComfyUiConstants.COMFYUI_TASK_INSTANCE_PORT;
import static com.frever.ml.comfy.ComfyUiConstants.COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING;
import static com.frever.ml.comfy.ComfyUiConstants.COMFYUI_TASK_INSTANCE_SETUP_TO_NUMBER_OF_CHECKS_BEFORE_ACTION;
import static com.frever.ml.comfy.ComfyUiConstants.PROTOCOL;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetupManager.TreatQueueInfoMissing.AS_MAX_QUEUE_LENGTH;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetupManager.TreatQueueInfoMissing.AS_MIN_QUEUE_LENGTH;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetupManager.TreatQueueInfoMissing.getQueueInfoMissingResult;
import static com.frever.ml.utils.Utils.addPortToServerIpIfNeeded;
import static com.frever.ml.utils.Utils.invalidQueueUrl;
import static com.frever.ml.utils.Utils.isDev;
import static java.util.concurrent.TimeUnit.MINUTES;
import static software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.ComfyProperties;
import com.frever.ml.comfy.ComfyUiAutoscalingCheck;
import com.frever.ml.comfy.dto.ComfyUiWorkflow;
import com.frever.ml.dao.ComfyUiAutoscalingCheckDao;
import com.frever.ml.utils.Utils;
import io.quarkus.arc.Lock;
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.context.api.ManagedExecutorConfig;
import io.smallrye.context.api.NamedInstance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.microprofile.context.ManagedExecutor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.Instance;
import software.amazon.awssdk.services.autoscaling.model.ScalingActivityInProgressException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Singleton
@Unremovable
public class ComfyUiTaskInstanceSetupManager {
    @Inject
    protected ComfyProperties comfyProperties;
    @Inject
    protected ObjectMapper objectMapper;
    @Inject
    protected Client client;
    @Inject
    protected ComfyUiAutoscalingCheckDao autoscalingCheckDao;
    @Inject
    @ManagedExecutorConfig(maxAsync = 10, maxQueued = 5)
    @NamedInstance("autoscaling")
    ManagedExecutor autoscalingExecutor;
    @Inject
    ComfyUiAutoscalingCheckDao comfyUiAutoscalingCheckDao;

    static final JsonPointer QUEUE_REMAINING = JsonPointer.compile("/exec_info/queue_remaining");
    static final int AUTOSCALING_CHECK_INTERVAL = 60;
    AutoScalingClient autoScalingClient;
    Ec2Client ec2Client;
    SqsClient sqs;
    String lipSyncTaskAutoScalingGroupName;
    String pulidTaskAutoScalingGroupName;
    String makeupTaskAutoScalingGroupName;
    Map<String, String> devInstanceWhenAutoscalingGroupEmpty = new ConcurrentHashMap<>();
    Map<ComfyUiTaskInstanceSetup, String> comfyUiTaskInstanceSetupToAutoscalingGroupName;
    Map<String, ComfyUiTaskInstanceSetup> comfyUiAutoScalingGroupToTaskInstanceSetup;

    @PostConstruct
    protected void init() {
        autoScalingClient = AutoScalingClient.builder().region(Region.EU_CENTRAL_1).build();
        ec2Client = Ec2Client.builder().region(Region.EU_CENTRAL_1).build();
        sqs = SqsClient.builder().region(Region.EU_CENTRAL_1).build();
        lipSyncTaskAutoScalingGroupName = comfyProperties.lipSyncTaskAutoScalingGroupName();
        pulidTaskAutoScalingGroupName = comfyProperties.pulidTaskAutoScalingGroupName();
        makeupTaskAutoScalingGroupName = comfyProperties.makeupTaskAutoScalingGroupName();
        devInstanceWhenAutoscalingGroupEmpty.put(
            lipSyncTaskAutoScalingGroupName,
            comfyProperties.comfyUiLipSyncInstanceAddress()
        );
        devInstanceWhenAutoscalingGroupEmpty.put(
            pulidTaskAutoScalingGroupName,
            comfyProperties.comfyUiPulidInstanceAddress()
        );
        devInstanceWhenAutoscalingGroupEmpty.put(
            makeupTaskAutoScalingGroupName,
            comfyProperties.comfyUiMakeupInstanceAddress()
        );
        comfyUiTaskInstanceSetupToAutoscalingGroupName = Map.of(
            ComfyUiTaskInstanceSetup.LipSync, comfyProperties.lipSyncTaskAutoScalingGroupName(),
            ComfyUiTaskInstanceSetup.Pulid, comfyProperties.pulidTaskAutoScalingGroupName(),
            ComfyUiTaskInstanceSetup.Makeup, comfyProperties.makeupTaskAutoScalingGroupName()
        );
        comfyUiAutoScalingGroupToTaskInstanceSetup = Map.of(
            comfyProperties.lipSyncTaskAutoScalingGroupName(), ComfyUiTaskInstanceSetup.LipSync,
            comfyProperties.pulidTaskAutoScalingGroupName(), ComfyUiTaskInstanceSetup.Pulid,
            comfyProperties.makeupTaskAutoScalingGroupName(), ComfyUiTaskInstanceSetup.Makeup
        );
    }

    @PreDestroy
    protected void shutdown() {
        if (autoScalingClient != null) {
            try {
                autoScalingClient.close();
            } catch (Exception e) {
                Log.warnf(e, "Failed to close autoScalingClient");
            }
        }
        if (ec2Client != null) {
            try {
                ec2Client.close();
            } catch (Exception e) {
                Log.warnf(e, "Failed to close ec2Client");
            }
        }
        if (sqs != null) {
            try {
                sqs.close();
            } catch (Exception e) {
                Log.warnf(e, "Failed to close sqs");
            }
        }
    }

    @Scheduled(every = "1m", delay = 2)
    @Lock(value = Lock.Type.WRITE, time = AUTOSCALING_CHECK_INTERVAL, unit = TimeUnit.SECONDS)
    void checkAutoscalingStatus() {
        if (isDev()) {
            Log.info("Not in prod, skipping autoscaling check");
            return;
        }
        var maxTaskQueueLengthForAllTaskSetup = getMaxTaskQueueLengthForAllTaskSetup();
        var autoscalingGroupsSizeInfo = getCurrentSizeInfoForAutoscalingGroups();
        for (var entry : maxTaskQueueLengthForAllTaskSetup.entrySet()) {
            var setup = entry.getKey();
            var sizeInfo = autoscalingGroupsSizeInfo.get(setup);
            if (sizeInfo.min() == 0 && sizeInfo.desired() == 0) {
                Log.infof("Autoscaling group %s is empty, skip this setup", setup);
                continue;
            }
            var queueLength = entry.getValue();
            var threshold = COMFYUI_TASK_INSTANCE_SETUP_TO_MAX_TASKS_IN_QUEUE_BEFORE_SCALING.get(setup);
            if (queueLength > 2 * threshold) {
                Log.infof(
                    "Task setup: %s, queue length: %d, threshold: %d, sizeInfo: %s, scaling up immediately",
                    setup,
                    queueLength,
                    threshold,
                    sizeInfo
                );
                triggerScaleUp(sizeInfo, setup, true);
            } else if (queueLength >= threshold) {
                triggerScaleUp(sizeInfo, setup, false);
            } else if (queueLength == 0) {
                triggerScaleIn(sizeInfo, setup);
            } else {
                resetCheckStatus(setup);
                Log.infof(
                    "Task setup: %s, queue length: %d, threshold: %d, asg-size-info: %s, no action needed",
                    setup,
                    queueLength,
                    threshold,
                    sizeInfo
                );
            }
        }
    }

    private void resetCheckStatus(ComfyUiTaskInstanceSetup setup) {
        comfyUiAutoscalingCheckDao.comfyUiAutoscalingCheck(
            setup, existing -> {
                Instant now = Instant.now();
                var checkIntervalAgo = now.minusSeconds(AUTOSCALING_CHECK_INTERVAL);
                if (existing.getCheckedAt().isAfter(checkIntervalAgo)) {
                    Log.infof("Setup %s last checked at: %s, no action needed", setup, existing.getCheckedAt());
                } else {
                    existing.setCheckedAt(Instant.now());
                    existing.setAboveThresholdSince(null);
                    existing.setBelowThresholdSince(null);

                }
                return existing;
            }
        );
    }

    private void triggerScaleIn(AutoScalingGroupSizeInfo sizeInfo, ComfyUiTaskInstanceSetup setup) {
        if (sizeInfo.current() > sizeInfo.desired()) {
            Log.infof(
                "For setup %s there is an ongoing scaling in, no action needed, current size: %d, desired size: %d",
                setup,
                sizeInfo.current(),
                sizeInfo.desired()
            );
        } else if (sizeInfo.current() <= sizeInfo.min()) {
            Log.infof(
                "Setup %s already at min size, no scaling in, current: %d, min: %d",
                setup,
                sizeInfo.current(),
                sizeInfo.min()
            );
        } else {
            comfyUiAutoscalingCheckDao.comfyUiAutoscalingCheck(
                setup, existing -> {
                    Instant now = Instant.now();
                    var checkIntervalAgo = now.minusSeconds(AUTOSCALING_CHECK_INTERVAL);
                    if (existing.getCheckedAt().isAfter(checkIntervalAgo)) {
                        Log.infof("Setup %s last checked at: %s, no action needed", setup, existing.getCheckedAt());
                    } else {
                        var belowThresholdSince = existing.getBelowThresholdSince();
                        var triggerScaleIn = COMFYUI_TASK_INSTANCE_SETUP_TO_NUMBER_OF_CHECKS_BEFORE_ACTION.get(setup);
                        if (belowThresholdSince == null) {
                            existing.setBelowThresholdSince(now);
                            existing.setCheckedAt(now);
                            existing.setAboveThresholdSince(null);
                        } else if (now.minusSeconds(MINUTES.toSeconds(triggerScaleIn)).isAfter(belowThresholdSince)) {
                            Log.infof(
                                "Setup %s last checked at: %s, already below threshold since: %s, scale in",
                                setup,
                                existing.getCheckedAt(),
                                existing.getBelowThresholdSince()
                            );
                            var autoscalingGroupName = comfyUiTaskInstanceSetupToAutoscalingGroupName.get(setup);
                            existing.setCheckedAt(Instant.now());
                            existing.setAboveThresholdSince(null);
                            existing.setBelowThresholdSince(null);
                            int desiredCapacity = sizeInfo.current() - 1;
                            Log.infof(
                                "Setup %s last checked at: %s, scaling in to %d",
                                setup,
                                existing.getCheckedAt(),
                                desiredCapacity
                            );
                            try {
                                autoScalingClient.setDesiredCapacity(builder -> {
                                    builder.autoScalingGroupName(autoscalingGroupName)
                                        .desiredCapacity(desiredCapacity)
                                        .honorCooldown(true);
                                });
                            } catch (ScalingActivityInProgressException e) {
                                Log.infof(
                                    "Scaling activity in progress for asg %s, no action needed for now, %s",
                                    autoscalingGroupName,
                                    e.getMessage()
                                );
                            }
                        } else {
                            existing.setCheckedAt(now);
                            existing.setAboveThresholdSince(null);
                        }
                    }
                    return existing;
                }
            );
        }

    }

    private void triggerScaleUp(
        AutoScalingGroupSizeInfo sizeInfo,
        ComfyUiTaskInstanceSetup setup,
        boolean scaleUpImmediately
    ) {
        var autoscalingGroupName = comfyUiTaskInstanceSetupToAutoscalingGroupName.get(setup);
        if (sizeInfo.current() >= sizeInfo.max()) {
            Log.warnf(
                "Setup %s already at max size, no scaling up, current: %d, max: %d",
                setup,
                sizeInfo.current(),
                sizeInfo.max()
            );
        } else if (sizeInfo.desired() > sizeInfo.current()) {
            Log.infof(
                "Setup %s already have ongoing scaling up, no action needed for now, current: %d, desired: %d",
                setup,
                sizeInfo.current(),
                sizeInfo.desired()
            );
        } else {
            comfyUiAutoscalingCheckDao.comfyUiAutoscalingCheck(
                setup, existing -> {
                    Instant now = Instant.now();
                    var checkIntervalAgo = now.minusSeconds(AUTOSCALING_CHECK_INTERVAL);
                    if (existing.getCheckedAt().isAfter(checkIntervalAgo)) {
                        Log.infof("Setup %s last checked at: %s, no action needed", setup, existing.getCheckedAt());
                    } else if (!scaleUpImmediately) {
                        var aboveThresholdSince = existing.getAboveThresholdSince();
                        var triggerScaleUp = COMFYUI_TASK_INSTANCE_SETUP_TO_NUMBER_OF_CHECKS_BEFORE_ACTION.get(setup);
                        if (aboveThresholdSince == null) {
                            existing.setAboveThresholdSince(now);
                            existing.setBelowThresholdSince(null);
                            existing.setCheckedAt(now);
                            Log.infof(
                                "Setup %s last checked at: %s, above threshold since is null, set to: %s",
                                setup,
                                existing.getCheckedAt(),
                                existing.getAboveThresholdSince()
                            );
                        } else if (now.minusSeconds(MINUTES.toSeconds(triggerScaleUp)).isAfter(aboveThresholdSince)) {
                            Log.infof(
                                "Setup %s last checked at: %s, already above threshold since: %s, scale up",
                                setup,
                                existing.getCheckedAt(),
                                existing.getAboveThresholdSince()
                            );
                            scaleUp(existing, sizeInfo, autoscalingGroupName);
                        } else {
                            existing.setBelowThresholdSince(null);
                            existing.setCheckedAt(now);
                        }
                    } else {
                        scaleUp(existing, sizeInfo, autoscalingGroupName);
                    }
                    return existing;
                }
            );
        }
    }

    private void scaleUp(
        ComfyUiAutoscalingCheck existing,
        AutoScalingGroupSizeInfo sizeInfo,
        String autoscalingGroupName
    ) {
        existing.setCheckedAt(Instant.now());
        existing.setAboveThresholdSince(null);
        existing.setBelowThresholdSince(null);
        int desiredCapacity = sizeInfo.current() + 1;
        Log.infof(
            "Asg %s last checked at: %s, scaling up to %d",
            autoscalingGroupName,
            existing.getCheckedAt(),
            desiredCapacity
        );
        try {
            autoScalingClient.setDesiredCapacity(builder -> {
                builder.autoScalingGroupName(autoscalingGroupName)
                    .desiredCapacity(desiredCapacity)
                    .honorCooldown(true);
            });
        } catch (ScalingActivityInProgressException e) {
            Log.infof(
                "Scaling activity in progress for asg %s, no action needed for now, %s",
                autoscalingGroupName,
                e.getMessage()
            );
        }
    }

    private Map<ComfyUiTaskInstanceSetup, AutoScalingGroupSizeInfo> getCurrentSizeInfoForAutoscalingGroups() {
        var autoScalingGroups = comfyUiAutoScalingGroupToTaskInstanceSetup.keySet();
        var autoScalingGroupsResponse =
            autoScalingClient.describeAutoScalingGroups(builder -> builder.autoScalingGroupNames(autoScalingGroups));
        return autoScalingGroupsResponse.autoScalingGroups()
            .stream()
            .collect(Collectors.toMap(
                asg -> comfyUiAutoScalingGroupToTaskInstanceSetup.get(asg.autoScalingGroupName()),
                asg -> {
                    int desired = asg.desiredCapacity();
                    int max = asg.maxSize();
                    int min = asg.minSize();
                    int current = asg.instances().size();
                    return AutoScalingGroupSizeInfo.of(max, min, desired, current);
                }
            ));
    }

    public String getServerAddressFromWorkflow(ComfyUiWorkflow workflow) {
        if (workflow == null) {
            throw new IllegalArgumentException("workflow is null");
        }
        var setup = workflow.getInstanceSetup();
        return getServerAddressFromSetup(setup);
    }

    private String getServerAddressFromSetup(ComfyUiTaskInstanceSetup setup) {
        if (setup == null) {
            throw new IllegalArgumentException("setup is null");
        }
        return switch (setup) {
            case LipSync -> getLipSyncServerAddress();
            case Pulid -> getPulidServerAddress();
            case Makeup -> getMakeupServerAddress();
        };
    }

    private String getLipSyncServerAddress() {
        return getLipSyncServerAddressAndCurrentQueueLength().serverAddress();
    }

    public ServerAddressAndCurrentQueueLength getLipSyncServerAddressAndCurrentQueueLength() {
        if (Utils.isDev()) {
            return new ServerAddressAndCurrentQueueLength(
                comfyProperties.comfyUiLipSyncInstanceAddress(),
                0
            );
        } else if (Utils.isProd()) {
            return findComfyUiTaskInstanceWithShortestQueueInAutoscalingGroup(lipSyncTaskAutoScalingGroupName);
        } else {
            throw new IllegalStateException("Not in dev or prod mode");
        }
    }

    // always return 0 as queue length for dev instance, as we only have 1 task instance for dev env.
    private ServerAddressAndCurrentQueueLength findComfyUiTaskInstanceWithShortestQueueInAutoscalingGroup(String autoscalingGroupName) {
        var minEntry = getTaskInstanceQueueInfoForAutoscalingGroup(autoscalingGroupName, AS_MAX_QUEUE_LENGTH)
            .min(Map.Entry.comparingByValue())
            .orElse(null);
        if (minEntry != null) {
            Log.infof(
                "From asg %s, the shortest queue length is %d from server %s.",
                autoscalingGroupName,
                minEntry.getValue(),
                minEntry.getKey()
            );
            return new ServerAddressAndCurrentQueueLength(
                addPortToServerIpIfNeeded(minEntry.getKey()),
                minEntry.getValue()
            );
        } else {
            Log.warnf("No min entry found for autoscaling group: %s", autoscalingGroupName);
            return new ServerAddressAndCurrentQueueLength(
                devInstanceWhenAutoscalingGroupEmpty.get(autoscalingGroupName),
                0
            );
        }
    }

    private String getPulidServerAddress() {
        return getPulidServerAddressAndCurrentQueueLength().serverAddress();
    }

    public ServerAddressAndCurrentQueueLength getPulidServerAddressAndCurrentQueueLength() {
        if (Utils.isDev()) {
            return new ServerAddressAndCurrentQueueLength(
                comfyProperties.comfyUiPulidInstanceAddress(),
                0
            );
        } else if (Utils.isProd()) {
            return findComfyUiTaskInstanceWithShortestQueueInAutoscalingGroup(pulidTaskAutoScalingGroupName);
        } else {
            throw new IllegalStateException("Not in dev or prod mode");
        }
    }

    private String getMakeupServerAddress() {
        return getMakeupServerAddressAndCurrentQueueLength().serverAddress();
    }

    public ServerAddressAndCurrentQueueLength getMakeupServerAddressAndCurrentQueueLength() {
        if (Utils.isDev()) {
            return new ServerAddressAndCurrentQueueLength(
                comfyProperties.comfyUiMakeupInstanceAddress(),
                0
            );
        } else if (Utils.isProd()) {
            return findComfyUiTaskInstanceWithShortestQueueInAutoscalingGroup(makeupTaskAutoScalingGroupName);
        } else {
            throw new IllegalStateException("Not in dev or prod mode");
        }
    }

    public Map<ComfyUiTaskInstanceSetup, Integer> getMaxTaskQueueLengthForAllTaskSetup() {
        return comfyUiTaskInstanceSetupToAutoscalingGroupName.entrySet().stream().map(entry -> {
            var setup = entry.getKey();
            var autoscalingGroupName = entry.getValue();
            var maxEntry = getTaskInstanceQueueInfoForAutoscalingGroup(autoscalingGroupName, AS_MIN_QUEUE_LENGTH)
                .max(Map.Entry.comparingByValue())
                .orElse(null);
            if (maxEntry != null) {
                return Map.entry(setup, maxEntry.getValue());
            } else {
                Log.warnf("No max entry found for autoscaling group: %s", autoscalingGroupName);
                return Map.entry(setup, 0);
            }
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Stream<Map.Entry<String, Integer>> getTaskInstanceQueueInfoForAutoscalingGroup(
        String autoscalingGroupName,
        TreatQueueInfoMissing treatQueueInfoMissing
    ) {
        List<String> privateIps = getAllInstancePrivateIpsFromAutoscalingGroup(autoscalingGroupName);
        if (privateIps.isEmpty()) {
            Log.warnf(
                "No IP addresses found for task, autoscaling group: %s, return queue length from dev env.",
                autoscalingGroupName
            );
            return Stream.of(Map.entry(devInstanceWhenAutoscalingGroupEmpty.get(autoscalingGroupName), 0));
        }
        var tasks = privateIps.stream().collect(Collectors.toMap(
            ip -> ip, ip -> {
                var serverAddress = ip + ":" + COMFYUI_TASK_INSTANCE_PORT;
                return autoscalingExecutor.supplyAsync(() -> comfyUiQueueRemaining(
                    serverAddress,
                    treatQueueInfoMissing
                ));
            }
        ));
        return tasks.entrySet().stream()
            .map(task -> {
                try {
                    return Map.entry(task.getKey(), task.getValue().get());
                } catch (Exception e) {
                    Log.warnf(e, "Failed to get queue remaining for %s", task.getKey());
                    return Map.entry(
                        task.getKey(),
                        TreatQueueInfoMissing.getQueueInfoMissingResult(treatQueueInfoMissing)
                    );
                }
            });
    }

    private List<String> getAllInstancePrivateIpsFromAutoscalingGroup(String autoscalingGroupName) {
        var autoScalingGroupsResponse =
            autoScalingClient.describeAutoScalingGroups(builder -> builder.autoScalingGroupNames(autoscalingGroupName));
        return autoScalingGroupsResponse.autoScalingGroups().stream().flatMap(autoScalingGroup -> {
            var instances = autoScalingGroup.instances().stream().map(Instance::instanceId).toList();
            if (instances.isEmpty()) {
                return Stream.empty();
            }
            var describeInstancesResponse = ec2Client.describeInstances(builder -> {
                builder.instanceIds(instances);
            });
            return describeInstancesResponse.reservations()
                .stream()
                .flatMap(reservation -> reservation.instances()
                    .stream()
                    .map(software.amazon.awssdk.services.ec2.model.Instance::privateIpAddress));
        }).toList();
    }

    public List<String> getLipSyncServerAddresses() {
        if (Utils.isDev()) {
            return List.of(comfyProperties.comfyUiLipSyncInstanceAddress());
        } else if (Utils.isProd()) {
            var privateIps = getAllInstancePrivateIpsFromAutoscalingGroup(lipSyncTaskAutoScalingGroupName);
            if (privateIps.isEmpty()) {
                Log.warnf(
                    "No IP addresses found for task, autoscaling group: %s, will use dev instance.",
                    lipSyncTaskAutoScalingGroupName
                );
                return List.of(comfyProperties.comfyUiLipSyncInstanceAddress());
            }
            return serverAddressesFromIps(privateIps);
        } else {
            throw new IllegalStateException("Not in dev or prod mode");
        }
    }

    public List<String> getPulidServerAddresses() {
        if (Utils.isDev()) {
            return List.of(comfyProperties.comfyUiPulidInstanceAddress());
        } else if (Utils.isProd()) {
            var privateIps = getAllInstancePrivateIpsFromAutoscalingGroup(pulidTaskAutoScalingGroupName);
            if (privateIps.isEmpty()) {
                Log.warnf(
                    "No IP addresses found for task, autoscaling group: %s, will use dev instance.",
                    pulidTaskAutoScalingGroupName
                );
                return List.of(comfyProperties.comfyUiPulidInstanceAddress());
            }
            return serverAddressesFromIps(privateIps);
        } else {
            throw new IllegalStateException("Not in dev or prod mode");
        }
    }

    public List<String> getMakeupServerAddresses() {
        if (Utils.isDev()) {
            return List.of(comfyProperties.comfyUiMakeupInstanceAddress());
        } else if (Utils.isProd()) {
            var privateIps = getAllInstancePrivateIpsFromAutoscalingGroup(makeupTaskAutoScalingGroupName);
            if (privateIps.isEmpty()) {
                Log.warnf(
                    "No IP addresses found for task, autoscaling group: %s, will use dev instance.",
                    makeupTaskAutoScalingGroupName
                );
                return List.of(comfyProperties.comfyUiMakeupInstanceAddress());
            }
            return serverAddressesFromIps(privateIps);
        } else {
            throw new IllegalStateException("Not in dev or prod mode");
        }
    }

    public int comfyUiQueueRemaining(String serverAddress) {
        return comfyUiQueueRemaining(serverAddress, null);
    }

    private int comfyUiQueueRemaining(String serverAddress, TreatQueueInfoMissing treatQueueInfoMissing) {
        int queueInfoMissingResult = getQueueInfoMissingResult(treatQueueInfoMissing);
        try (Response response = client.target(PROTOCOL + serverAddress + "/prompt")
            .request()
            .get()) {
            var entity = response.readEntity(String.class);
            if (response.getStatus() != 200) {
                Log.warnf("Failed to get queue status, status: %d, result: %s", response.getStatus(), entity);
                return queueInfoMissingResult;
            }
            return objectMapper.readTree(entity).at(QUEUE_REMAINING).asInt();
        } catch (Exception e) {
            Log.warnf(e, "Failed to check ComfyUi queue Remaining.");
            return queueInfoMissingResult;
        }
    }

    public int sqsRemaining(String queueUrl) {
        if (invalidQueueUrl(queueUrl)) {
            Log.error("QueueUrl is null or blank or not Sqs, will not consume messages.");
            return -1;
        }
        try {
            return Integer.parseInt(sqs.getQueueAttributes(builder -> builder.queueUrl(queueUrl)
                .attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES)).attributes().get(APPROXIMATE_NUMBER_OF_MESSAGES));
        } catch (Exception e) {
            Log.warnf(e, "Failed to get SQS remaining messages.");
            return 0;
        }
    }

    private static List<String> serverAddressesFromIps(List<String> serverIps) {
        return serverIps.stream().map(Utils::addPortToServerIpIfNeeded).toList();
    }

    public ComfyUiTaskInstanceSetup getTaskInstanceSetupFromAutoscalingGroupName(String autoscalingGroupName) {
        return comfyUiAutoScalingGroupToTaskInstanceSetup.get(autoscalingGroupName);
    }

    enum TreatQueueInfoMissing {
        AS_MAX_QUEUE_LENGTH,
        AS_MIN_QUEUE_LENGTH;

        static int getQueueInfoMissingResult(TreatQueueInfoMissing treatQueueInfoMissing) {
            return switch (treatQueueInfoMissing) {
                case AS_MAX_QUEUE_LENGTH -> Integer.MAX_VALUE;
                case AS_MIN_QUEUE_LENGTH -> Integer.MIN_VALUE;
                case null -> -1;
            };
        }
    }

    record AutoScalingGroupSizeInfo(int max, int min, int desired, int current) {
        public static AutoScalingGroupSizeInfo of(int max, int min, int desired, int current) {
            return new AutoScalingGroupSizeInfo(max, min, desired, current);
        }
    }

    public record ServerAddressAndCurrentQueueLength(String serverAddress, int currentQueueLength) {
    }
}
