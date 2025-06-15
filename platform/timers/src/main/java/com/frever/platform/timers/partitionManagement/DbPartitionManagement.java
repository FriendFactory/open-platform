package com.frever.platform.timers.partitionManagement;

import com.frever.platform.timers.utils.Utils;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.List;

@Singleton
@Transactional
public class DbPartitionManagement {
    private static final List<String> YEARLY_PARTITIONED_TABLES =
        List.of("ChatMessageLike", "Comments", "GranularLikes", "Likes", "Remixes", "Shares", "Views");
    private static final List<String> MONTHLY_PARTITIONED_TABLES = List.of("ChatMessage");
    private static final int YEARLY_PARTITION_AHEAD = 2;
    private static final int MONTHLY_PARTITION_AHEAD = 3;
    private static final String PARTITION_TEMPLATE =
        "CREATE TABLE IF NOT EXISTS \"%s\" PARTITION OF \"%s\" FOR VALUES FROM ('%s') TO ('%s')";

    @Inject
    protected AgroalDataSource mainDataSource;

    @Scheduled(every = "P1d", delayed = "7m")
    public void createMonthlyPartitions() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            for (var table : MONTHLY_PARTITIONED_TABLES) {
                var monthlyPartitionInfo = Utils.generateMonthlyPartitionInfo(table, MONTHLY_PARTITION_AHEAD);
                for (var partitionInfo : monthlyPartitionInfo) {
                    var sql = String.format(
                        PARTITION_TEMPLATE,
                        partitionInfo.partitionName(),
                        partitionInfo.tableName(),
                        partitionInfo.begin(),
                        partitionInfo.end()
                    );
                    Log.infof("CreateMonthlyPartitions Will run: %s", sql);
                    statement.execute(sql);
                }
            }
        } catch (SQLException e) {
            Log.error("Failed to run create monthly partitions.", e);
        }
    }

    @Scheduled(every = "P5d", delayed = "11m")
    public void createYearlyPartitions() {
        try (var connection = mainDataSource.getConnection(); var statement = connection.createStatement()) {
            for (var table : YEARLY_PARTITIONED_TABLES) {
                var yearlyPartitionInfo = Utils.generateYearlyPartitionInfo(table, YEARLY_PARTITION_AHEAD);
                for (var partitionInfo : yearlyPartitionInfo) {
                    var sql = String.format(
                        PARTITION_TEMPLATE,
                        partitionInfo.partitionName(),
                        partitionInfo.tableName(),
                        partitionInfo.begin(),
                        partitionInfo.end()
                    );
                    Log.infof("CreateYearlyPartitions Will run: %s", sql);
                    statement.execute(sql);
                    if ("Likes".equals(partitionInfo.tableName())) {
                        var replicaIdentity =
                            "ALTER TABLE \"" + partitionInfo.partitionName() + "\" REPLICA IDENTITY FULL";
                        Log.infof("Will run for Likes table: %s", replicaIdentity);
                        statement.execute(replicaIdentity);
                    }
                }
            }
        } catch (SQLException e) {
            Log.error("Failed to run create yearly partitions.", e);
        }
    }
}
