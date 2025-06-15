package com.frever.ml.dao;

import static com.frever.ml.comfy.ComfyUiTask.DEFAULT_DURATION;

import com.frever.ml.comfy.ComfyUiTask;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ComfyUiTaskDaoTest extends DaoTestBase {
    public static final String COMFYUI_FILE_NAME = "comfyUiTaskTest";

    @Inject
    ComfyUiTaskDao comfyUiTaskDao;

    @Test
    public void testGetComfyUiTasks() {
        var comfyUiTask =
            ComfyUiTask.createTask("default", SERVER_IP, COMFYUI_FILE_NAME, "", DEFAULT_DURATION, 1, 1, 1, "abcd");
        comfyUiTask.setPromptId(UUID.randomUUID());
        var id = comfyUiTaskDao.createTask(comfyUiTask);

        var get = comfyUiTaskDao.getComfyUiTask(id);
        Assertions.assertTrue(get.getCreatedAt().until(Instant.now(), ChronoUnit.SECONDS) < 2);
        Assertions.assertEquals(1, get.getGroupId());
        Assertions.assertEquals(COMFYUI_FILE_NAME, get.getFileName());
        Assertions.assertEquals(SERVER_IP, get.getServerIp());
        Assertions.assertNull(get.getStartedAt());
        Assertions.assertNull(get.getFinishedAt());

        comfyUiTaskDao.markTaskStarted(comfyUiTask.getPromptId(), Instant.now().plusSeconds(1));
        get = comfyUiTaskDao.getComfyUiTask(id);
        Assertions.assertNotNull(get.getStartedAt());
        Assertions.assertTrue(get.getStartedAt().isAfter(get.getCreatedAt()));

        var durationCalculation = comfyUiTaskDao.getComfyUiUnfinishedTaskDurationItems(SERVER_IP);
        Assertions.assertEquals(1, durationCalculation.size());
        Assertions.assertEquals(DEFAULT_DURATION, durationCalculation.getFirst().duration());
        Assertions.assertNotNull(durationCalculation.getFirst().startedAt());

        comfyUiTaskDao.markTaskFinished(id);
        comfyUiTaskDao.markTaskFinished(comfyUiTask.getPromptId());
        get = comfyUiTaskDao.getComfyUiTask(id);
        Assertions.assertTrue(get.getFinishedAt().isAfter(get.getCreatedAt()));

        durationCalculation = comfyUiTaskDao.getComfyUiUnfinishedTaskDurationItems(SERVER_IP);
        Assertions.assertEquals(0, durationCalculation.size());

        long timestamp = 1736771452993L;
        Assertions.assertTrue(Instant.ofEpochMilli(timestamp).isBefore(Instant.now()));
    }
}
