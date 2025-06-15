package com.frever.ml.dao;

import com.frever.ml.comfy.setup.ComfyUiTaskInstanceSetup;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ComfyUiAutoscalingCheckDaoTest extends DaoTestBase {
    @Inject
    ComfyUiAutoscalingCheckDao comfyUiAutoscalingCheckDao;

    @Test
    public void testComfyUiAutoscalingCheck() {
        comfyUiAutoscalingCheckDao.comfyUiAutoscalingCheck(
            ComfyUiTaskInstanceSetup.Pulid, existingCheck -> {
                Assertions.assertNotNull(existingCheck);
                Assertions.assertNull(existingCheck.getAboveThresholdSince());
                existingCheck.setAboveThresholdSince(Instant.now());
                return existingCheck;
            }
        );
        comfyUiAutoscalingCheckDao.comfyUiAutoscalingCheck(
            ComfyUiTaskInstanceSetup.Pulid, existingCheck -> {
                Assertions.assertNotNull(existingCheck);
                Assertions.assertNotNull(existingCheck.getAboveThresholdSince());
                return existingCheck;
            }
        );
    }
}
