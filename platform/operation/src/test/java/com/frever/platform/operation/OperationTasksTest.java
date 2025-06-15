package com.frever.platform.operation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OperationTasksTest {
    private static final String DB_CONNECTION_STRING =
        "Host=production-main.cpcnb21agehx.eu-central-1.rds.amazonaws.com;Username=test;Password=test;Database=db;"
            + "Maximum Pool Size=30;Command Timeout=360;";

    @Test
    public void testGetDBConnectionParameters() {
        final Map<String, String> parameters = DbUtils.getDBConnectionParameters(DB_CONNECTION_STRING);
        assertAll(
            "DB connection parameters",
            () -> parameters.containsKey(DbUtils.USER_NAME),
            () -> parameters.containsKey(DbUtils.PASSWORD),
            () -> parameters.containsKey(DbUtils.HOST),
            () -> parameters.containsKey(DbUtils.DB_NAME)
        );
    }

    @Test
    public void testOneDayBefore() {
        Instant eventTime = Instant.now().minus(25, ChronoUnit.HOURS);
        assertTrue(S3Utils.oneDayBefore(eventTime));

        eventTime = Instant.now().minus(1, ChronoUnit.HOURS);
        assertFalse(S3Utils.oneDayBefore(eventTime));
    }

    @Test
    public void testDayBefore() {
        Instant eventTime = Instant.now().minus(4, ChronoUnit.DAYS);
        assertTrue(S3Utils.daysBefore(eventTime, 4));
        assertFalse(S3Utils.daysBefore(eventTime, 5));
    }
}
