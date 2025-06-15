package com.frever.ml.dao;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ComfyUiInstanceStatusReportDaoTest extends DaoTestBase {
    @Inject
    ComfyUiInstanceStatusReportDao comfyUiInstanceStatusReportDao;

    @Test
    public void testReport() {
        comfyUiInstanceStatusReportDao.addComfyUiInstance(SERVER_IP);
        Assertions.assertTrue(comfyUiInstanceStatusReportDao.comfyUiInstanceExists(SERVER_IP));
        Assertions.assertTrue(comfyUiInstanceStatusReportDao.beginReport(SERVER_IP));
        Assertions.assertTrue(comfyUiInstanceStatusReportDao.endReport(SERVER_IP));
        comfyUiInstanceStatusReportDao.markZeroReported(SERVER_IP);
        Assertions.assertTrue(comfyUiInstanceStatusReportDao.zeroReported(SERVER_IP));
        Assertions.assertTrue(comfyUiInstanceStatusReportDao.reportedWithinMinutes(SERVER_IP, 1));
        comfyUiInstanceStatusReportDao.markReport(SERVER_IP);
        Assertions.assertFalse(comfyUiInstanceStatusReportDao.zeroReported(SERVER_IP));
        Assertions.assertTrue(comfyUiInstanceStatusReportDao.reportedWithinMinutes(SERVER_IP, 1));
    }

}
