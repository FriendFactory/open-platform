package com.frever.ml.comfy;

import static com.frever.ml.comfy.ComfyUiManager.CONNECTIVITY_CHECK_INTERVAL;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.LipSync;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.Makeup;
import static com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup.Pulid;

import com.frever.ml.dao.ComfyUiTaskDao;
import com.frever.ml.utils.Utils;
import io.quarkus.arc.Lock;
import io.quarkus.arc.Unremovable;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
@Unremovable
public class ComfyUiScheduler {
    @Inject
    protected ComfyUiTaskDao comfyUiTaskDao;
    @Inject
    protected ComfyUiManager comfyUiManager;
    @Inject
    protected ComfyProperties comfyProperties;

    @Scheduled(every = "8h", delay = 1)
    @Lock(value = Lock.Type.WRITE, time = CONNECTIVITY_CHECK_INTERVAL * 5, unit = TimeUnit.SECONDS)
    protected void clearFinishedComfyUiTasks() {
        comfyUiTaskDao.clearFinishedTasksOlderThanOneDay();
    }

    @Scheduled(every = "8h", delay = 5)
    @Lock(value = Lock.Type.WRITE, time = CONNECTIVITY_CHECK_INTERVAL * 5, unit = TimeUnit.SECONDS)
    protected void markUnfinishedComfyUiTasksFinished() {
        comfyUiTaskDao.markUnfinishedTasksFinished();
    }

    @Scheduled(cron = "0 33 8 * * ?", timeZone = "CET")
    protected void initializeComfyUiCache() {
        if (Utils.isDev()) {
            comfyUiManager.cacheComponents(comfyProperties.comfyUiPulidInstanceAddress(), Pulid);
            comfyUiManager.cacheComponents(comfyProperties.comfyUiMakeupInstanceAddress(), Makeup);
            comfyUiManager.cacheComponents(comfyProperties.comfyUiLipSyncInstanceAddress(), LipSync);
        }
    }
}
