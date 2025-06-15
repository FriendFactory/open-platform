package com.frever.platform.timers.utils;

import com.frever.platform.timers.utils.entities.TimerExecution;
import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.jdbi.v3.core.Jdbi;

public abstract class AbstractAggregationService {
    @Inject
    protected EntityManager entityManager;
    @Inject
    protected AgroalDataSource mainDataSource;
    @Inject
    protected Jdbi jdbi;

    // For aggregating video_kpi and follower_stats
    protected static final int DelaySeconds = 120;

    protected abstract String getTimerName();

    protected void recordTimerExecution() {
        recordTimerExecution(getTimerName());
    }

    protected void recordTimerExecution(String timerName) {
        Instant start = Instant.now();
        TimerExecution timerExecution = new TimerExecution(timerName, start);
        entityManager.merge(timerExecution);
    }
}
