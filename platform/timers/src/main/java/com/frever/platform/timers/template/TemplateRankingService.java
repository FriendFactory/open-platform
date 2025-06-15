package com.frever.platform.timers.template;

import static com.frever.platform.timers.utils.Utils.inFreverProd;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
@Transactional
public class TemplateRankingService {
    private static final int BATCH_SIZE = 1000;
    @Inject
    TemplateRankingLoader templateRankingLoader;
    @Inject
    AgroalDataSource dataSource;

    @Scheduled(every = "8h", delay = 5)
    @Lock(value = Lock.Type.WRITE, time = 180, unit = TimeUnit.SECONDS)
    public void initTemplateRankingTable() {
        if (!inFreverProd()) {
            Log.info("Not in frever-prod, skipping table initialization.");
            return;
        }
        List<Long> templateRankings = templateRankingLoader.loadTemplateRankings();
        if (templateRankings.isEmpty()) {
            Log.warn("No template rankings found, skipping table initialization.");
            return;
        }
        int i = 1;
        try (var connection = dataSource.getConnection();
             var delete = connection.createStatement();
             var insert = connection.prepareStatement(
                 "INSERT INTO stats.template_ranking (template_id, rank) VALUES (?, ?)")) {
            delete.execute("delete from stats.template_ranking");
            for (Long templateRanking : templateRankings) {
                insert.setLong(1, templateRanking);
                insert.setInt(2, i++);
                insert.addBatch();
                if (i % BATCH_SIZE == 0) {
                    insert.executeBatch();
                }
            }
            if (i % BATCH_SIZE != 0) {
                insert.executeBatch();
            }
        } catch (SQLException e) {
            Log.error("Error initializing template ranking table.", e);
        }
    }
}
