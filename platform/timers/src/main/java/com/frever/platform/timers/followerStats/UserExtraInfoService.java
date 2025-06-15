package com.frever.platform.timers.followerStats;

import com.frever.platform.timers.utils.AbstractAggregationService;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.statement.PreparedBatch;

@ApplicationScoped
@Transactional
public class UserExtraInfoService extends AbstractAggregationService {
    public static final String TIMER_NAME = "update-user-extra-info";

    private static final String RECENT_USER_LOGIN = """
        SELECT "GroupId" as group_id, MAX("OccurredAt") AS occurred_at
        FROM "UserActivity"
        WHERE "ActionType" = 'Login' and "OccurredAt" >= now() - INTERVAL '12h'
        GROUP BY "GroupId";
        """;

    private static final String USER_LOGIN_BOOTSTRAP = """
        SELECT "GroupId" as group_id, MAX("OccurredAt") AS occurred_at
        FROM "UserActivity"
        WHERE "ActionType" = 'Login' and "OccurredAt" >= now() - INTERVAL '15d'
        GROUP BY "GroupId";
        """;

    @Scheduled(every = "11h", delay = 3)
    @Lock(value = Lock.Type.WRITE, time = 60, unit = TimeUnit.SECONDS)
    public void updateUserLastLogin() {
        Log.info("Updating latest login in user_extra_info table in 'stats' schema.");
        recordTimerExecution();
        Map<Long, Instant> groupIdToLastLoginMap =
            jdbi.withHandle(handle -> handle.createQuery(RECENT_USER_LOGIN).setMapKeyColumn("group_id")
                .setMapValueColumn("occurred_at")
                .collectInto(new GenericType<>() {
                }));
        Log.infof("%s groups have logged in recently.", groupIdToLastLoginMap.size());
        var sourceUpdated = jdbi.withHandle(handle -> updateLastLogin(handle, groupIdToLastLoginMap));
        long count = Arrays.stream(sourceUpdated).filter(i -> i > 0).count();
        Log.infof(
            "Should Update %s last_login in user_extra_info table in 'stats' schema, updated %s",
            sourceUpdated.length,
            count
        );
        Log.info("Done updating last_login in user_extra_info table in 'stats' schema.");
    }

    public void bootstrapUserLastLogin() {
        Log.info("Bootstrapping latest login in user_extra_info table in 'stats' schema.");
        recordTimerExecution();
        Map<Long, Instant> groupIdToLastLoginMap =
            jdbi.withHandle(handle -> handle.createQuery(USER_LOGIN_BOOTSTRAP).setMapKeyColumn("group_id")
                .setMapValueColumn("occurred_at")
                .collectInto(new GenericType<>() {
                }));
        Log.infof("%s groups have been added during bootstrap.", groupIdToLastLoginMap.size());
        var sourceUpdated = jdbi.withHandle(handle -> updateLastLogin(handle, groupIdToLastLoginMap));
        long count = Arrays.stream(sourceUpdated).filter(i -> i > 0).count();
        Log.infof(
            "Should update %s last_login in user_extra_info table in 'stats' schema, updated %s",
            sourceUpdated.length,
            count
        );
        Log.info("Done bootstrapping latest login in user_extra_info table in 'stats' schema.");
    }

    @Scheduled(every = "24h", delay = 19)
    @Lock(value = Lock.Type.WRITE, time = 60, unit = TimeUnit.SECONDS)
    public void removeInactiveUsers() {
        Log.info("Removing inactive users from user_extra_info table in 'stats' schema.");
        recordTimerExecution("remove-inactive-users");
        List<Long> deleted = jdbi.withHandle(handle -> handle.createQuery(
                "DELETE FROM stats.user_extra_info WHERE last_login < now() - INTERVAL '1 month' returning group_id")
            .mapTo(Long.class)
            .list());
        // TODO: Remove data in stats.follow_edge table for these users
        Log.infof("Deleted %s inactive users from user_extra_info table in 'stats' schema.", deleted.size());
        Log.info("Done removing inactive users from user_extra_info table in 'stats' schema.");
    }

    private static int[] updateLastLogin(Handle handle, Map<Long, Instant> groupIdToLastLoginMap) {
        PreparedBatch batch = handle.prepareBatch(
            "INSERT INTO stats.user_extra_info (group_id, last_login) VALUES (:groupId, :lastLogin) "
                + "ON CONFLICT (group_id) DO UPDATE SET last_login = EXCLUDED.last_login "
                + "where EXCLUDED.last_login > user_extra_info.last_login");
        for (var entry : groupIdToLastLoginMap.entrySet()) {
            batch.bind("lastLogin", entry.getValue()).bind("groupId", entry.getKey()).add();
        }
        return batch.execute();
    }

    @Override
    protected String getTimerName() {
        return TIMER_NAME;
    }
}
