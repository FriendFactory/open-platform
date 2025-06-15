package com.frever.ml.dao;

import static com.frever.ml.utils.Constants.FREVER_OFFICIAL_GROUP_ID;
import static com.frever.ml.utils.Constants.NUM_COMMON_FRIENDS_RECOMMENDED;
import static com.frever.ml.utils.Constants.NUM_FOLLOW_BACK_RECOMMENDED;
import static com.frever.ml.utils.Constants.NUM_INFLUENTIAL_RECOMMENDED;

import com.frever.ml.follow.recsys.SecondHopFriends;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import org.jdbi.v3.core.generic.GenericType;

@ApplicationScoped
public class FollowEdgeDao extends BaseDao {
    private static final String MOST_INFLUENTIAL_NODES = """
        select dest.destination
        from
        (
            select destination --, count(*) as count
            from stats.follow_edge
            inner join stats.user_extra_info uei on destination = uei.group_id
            where not exists (select 1 from "BlockedUser" where "BlockedUserId" = destination and "BlockedByUserId" = :groupId)
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = destination)
            and destination != :groupId and destination != :freverOfficial and source != :groupId
            and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
            and uei.last_login >= now() - INTERVAL '2 weeks'
            group by destination, uei.last_login
            order by count(source) desc, uei.last_login desc limit :limit1
        ) as dest
        where not exists (select 1 from "Group" where "Id" = dest.destination and ("IsBlocked" = true or "DeletedAt" is not null))
        limit :limit2
        """;

    private static final String MOST_INFLUENTIAL_NODES_OPTIMIZED = """
        select dest.destination from stats.influential_nodes dest
        where dest.destination != :groupId
        and not exists (select 1 from stats.follow_edge where source = :groupId and destination = dest.destination)
        and not exists (select 1 from "Group" where "Id" = destination and ("IsBlocked" = true or "DeletedAt" is not null))
        and not exists (select 1 from "BlockedUser" where "BlockedUserId" = dest.destination and "BlockedByUserId" = :groupId)
        and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = dest.destination)
        order by dest.rank
        limit :limit
        """;

    private static final String FIRST_HOP_FRIENDS = """
        select destination, is_mutual
        from stats.follow_edge
        inner join stats.user_extra_info uei on destination = uei.group_id
        where source = :groupId
        and not exists (select 1 from "BlockedUser" where "BlockedUserId" = destination and "BlockedByUserId" = :groupId)
        and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = destination)
        and not exists (select 1 from "Group" where "Id" = destination and ("IsBlocked" = true or "DeletedAt" is not null))
        and destination != :groupId and destination != :freverOfficial
        and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
        and uei.last_login >= now() - INTERVAL '2 weeks'
        """;

    private static final String SECOND_HOP_FRIENDS_WITH_LIMIT = """
        with first_hop as (
            select destination
            from stats.follow_edge
            inner join stats.user_extra_info uei on destination = uei.group_id
            where source = :groupId
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = destination and "BlockedByUserId" = :groupId)
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = destination)
            and not exists (select 1 from "Group" where "Id" = destination and ("IsBlocked" = true or "DeletedAt" is not null))
            and destination != :groupId and destination != :freverOfficial
            and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
            and uei.last_login >= now() - INTERVAL '2 weeks'
        ), second_hop as (
            select fe.destination as second_hop, (array_agg(fe.source))[1: :limit2] as common_friends
            from stats.follow_edge fe
            inner join first_hop on fe.source = first_hop.destination
            inner join stats.user_extra_info uei on fe.destination = uei.group_id
            where fe.destination != :groupId and fe.destination != :freverOfficial and fe.source != :groupId
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = fe.destination and "BlockedByUserId" = :groupId)
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = fe.destination)
            and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
            and uei.last_login >= now() - INTERVAL '2 weeks'
            and not exists (select 1 from first_hop fh where fh.destination = fe.destination)
            group by fe.destination
            order by count(fe.source) desc limit :limit1
        )
        select second_hop as groupId, common_friends from second_hop
        where not exists (select 1 from "Group" where "Id" = second_hop and ("IsBlocked" = true or "DeletedAt" is not null))
        """;

    private static final String SECOND_HOP_FRIENDS_FOR_EGO_NETWORK = """
        with first_hop as (
            select destination
            from stats.follow_edge
            inner join stats.user_extra_info uei on destination = uei.group_id
            where source = :groupId
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = destination and "BlockedByUserId" = :groupId)
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = destination)
            and not exists (select 1 from "Group" where "Id" = destination and ("IsBlocked" = true or "DeletedAt" is not null))
            and destination != :groupId and destination != :freverOfficial
            and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
            and uei.last_login >= now() - INTERVAL '2 weeks'
        ), second_hop as (
            select fe.destination as second_hop --, array_agg(fe.source) as common_friends
            from stats.follow_edge fe
            inner join first_hop on fe.source = first_hop.destination
            inner join stats.user_extra_info uei on fe.destination = uei.group_id
            where fe.destination != :groupId and fe.destination != :freverOfficial and fe.source != :groupId
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = fe.destination and "BlockedByUserId" = :groupId)
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = fe.destination)
            and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
            and uei.last_login >= now() - INTERVAL '2 weeks'
            and not exists (select 1 from first_hop fh where fh.destination = fe.destination)
            group by fe.destination
        )
        select second_hop.second_hop
        from second_hop
        where not exists (select 1 from "Group" where "Id" = second_hop and ("IsBlocked" = true or "DeletedAt" is not null))
        """;

    private static final String FOLLOW_BACK = """
        with successors as (
            select destination
            from stats.follow_edge
            inner join stats.user_extra_info uei on destination = uei.group_id
            where source = :groupId
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = destination and "BlockedByUserId" = :groupId)
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = destination)
            and not exists (select 1 from "Group" where "Id" = destination and ("IsBlocked" = true or "DeletedAt" is not null))
            and destination != :groupId and destination != :freverOfficial
            and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
            and uei.last_login >= now() - INTERVAL '2 weeks'
        ), predecessors as (
            select source
            from stats.follow_edge
            inner join stats.user_extra_info uei on source = uei.group_id
            where destination = :groupId
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = source and "BlockedByUserId" = :groupId)
            and not exists (select 1 from "BlockedUser" where "BlockedUserId" = :groupId and "BlockedByUserId" = source)
            and not exists (select 1 from "Group" where "Id" = source and ("IsBlocked" = true or "DeletedAt" is not null))
            and source != :groupId and source != :freverOfficial
            and not ((source_is_minor or destination_is_minor) and (source_strict_coppa_rules or destination_strict_coppa_rules))
            and uei.last_login >= now() - INTERVAL '2 weeks'
        )
        select * from predecessors
        where not exists (select 1 from successors where source = destination)
        limit :limit
        """;

    public List<Long> getMostInfluentialNodes(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(MOST_INFLUENTIAL_NODES)
            .bind("groupId", groupId)
            .bind("freverOfficial", FREVER_OFFICIAL_GROUP_ID)
            .bind("limit1", NUM_INFLUENTIAL_RECOMMENDED * 2)
            .bind("limit2", NUM_INFLUENTIAL_RECOMMENDED)
            .mapTo(Long.class)
            .list());
    }

    public List<Long> getMostInfluentialNodesOptimized(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(MOST_INFLUENTIAL_NODES_OPTIMIZED)
            .bind("groupId", groupId)
            .bind("limit", NUM_INFLUENTIAL_RECOMMENDED)
            .mapTo(Long.class)
            .list());
    }

    public List<Long> getFollowBack(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(FOLLOW_BACK)
            .bind("groupId", groupId)
            .bind("freverOfficial", FREVER_OFFICIAL_GROUP_ID)
            .bind("limit", NUM_FOLLOW_BACK_RECOMMENDED)
            .mapTo(Long.class)
            .list());
    }

    public Map<Long, Boolean> getFirstHopFriends(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(FIRST_HOP_FRIENDS)
            .bind("groupId", groupId)
            .bind("freverOfficial", FREVER_OFFICIAL_GROUP_ID)
            .setMapKeyColumn("destination")
            .setMapValueColumn("is_mutual")
            .collectInto(new GenericType<>() {
            }));
    }

    public List<SecondHopFriends> getSecondHopFriendsWithLimit(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(SECOND_HOP_FRIENDS_WITH_LIMIT)
            .bind("groupId", groupId)
            .bind("freverOfficial", FREVER_OFFICIAL_GROUP_ID)
            .bind("limit1", NUM_COMMON_FRIENDS_RECOMMENDED * 2)
            .bind("limit2", NUM_COMMON_FRIENDS_RECOMMENDED)
            .mapTo(SecondHopFriends.class)
            .list());
    }

    public List<Long> getAllSecondHopFriends(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(SECOND_HOP_FRIENDS_FOR_EGO_NETWORK)
            .bind("groupId", groupId)
            .bind("freverOfficial", FREVER_OFFICIAL_GROUP_ID)
            .mapTo(Long.class)
            .list());
    }
}
