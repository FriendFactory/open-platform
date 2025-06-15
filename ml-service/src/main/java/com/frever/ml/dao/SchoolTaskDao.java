package com.frever.ml.dao;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class SchoolTaskDao extends BaseDao {
    private static final String SCHOOL_TASKS = """
        SELECT "LevelId"
            FROM "SchoolTask"
        WHERE "LevelId" IS NOT NULL AND "ReadinessId" = 2 AND "CreatedTime" >= :start
        """;

    public List<Long> getSchoolTaskLevelIds() {
        var start = Instant.now().minus(10, ChronoUnit.DAYS);
        return jdbi.withHandle(handle -> handle.createQuery(SCHOOL_TASKS)
            .bind("start", start)
            .mapTo(Long.class)
            .list());
    }
}
