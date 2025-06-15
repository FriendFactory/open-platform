package com.frever.platform.timers.followerStats;

import com.frever.platform.timers.utils.AbstractAggregationService;
import io.quarkus.arc.Lock;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Transactional
public class CalculateInfluentialNodes extends AbstractAggregationService {
    public static final String TIMER_NAME = "calculate-influential-nodes";

    private static final String CLEANUP_MOST_INFLUENTIAL_NODES = """
        delete from stats.influential_nodes
        """;

    private static final String INFLUENTIAL_NODES = """
        insert into stats.influential_nodes (destination, rank)
        select destination, ROW_NUMBER() OVER (order by count(source) desc, uei.last_login desc) as rank
        from stats.follow_edge
        inner join stats.user_extra_info uei on destination = uei.group_id
        where destination != 742
        and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
        and uei.last_login >= now() - INTERVAL '2 weeks'
        group by destination, uei.last_login
        order by count(source) desc, uei.last_login desc limit 1200;
        """;

    @Scheduled(every = "7h", delay = 7)
    @Lock(value = Lock.Type.WRITE, time = 120, unit = TimeUnit.SECONDS)
    public void calculateMostInfluentialNodes() {
        Log.info("Calculating most influential nodes.");
        recordTimerExecution();
        int deleted = jdbi.withHandle(handle -> handle.execute(CLEANUP_MOST_INFLUENTIAL_NODES));
        Log.infof("Deleted %s influential nodes. ", deleted);
        int added = jdbi.withHandle(handle -> handle.execute(INFLUENTIAL_NODES));
        Log.infof("Added %s influential nodes. ", added);
    }

    @Override
    protected String getTimerName() {
        return TIMER_NAME;
    }
}
