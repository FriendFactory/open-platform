package com.frever.ml.dao;

import static com.frever.ml.utils.Constants.GROUP_IDS_TO_EXCLUDE;
import static com.frever.ml.utils.Constants.GROUP_IDS_WITH_1TEST_NICKNAME;

import com.frever.ml.dto.UserInfo;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jdbi.v3.core.generic.GenericType;

@ApplicationScoped
public class UserDao extends BaseDao {
    volatile Set<Long> excludedGroupIds;

    @PostConstruct
    public void init() {
        Set<Long> temp = new HashSet<>(GROUP_IDS_WITH_1TEST_NICKNAME.length + GROUP_IDS_TO_EXCLUDE.length);
        temp.addAll(Arrays.stream(GROUP_IDS_TO_EXCLUDE).boxed().toList());
        temp.addAll(Arrays.stream(GROUP_IDS_WITH_1TEST_NICKNAME).boxed().toList());
        excludedGroupIds = temp;
    }

    public Set<Long> getExcludedGroupIds() {
        return excludedGroupIds;
    }

    private static final String USER_INFO = """
        SELECT
            "Group"."Id" AS group_id,
            "UserAndGroup"."UserId" AS user_id,
            "Country"."ISOName" AS country,
            "Language"."IsoCode" AS language
        FROM "Group"
            INNER JOIN "UserAndGroup" ON "Group"."Id" = "UserAndGroup"."GroupId"
            INNER JOIN "Country" ON "Group"."TaxationCountryId" = "Country"."Id"
            INNER JOIN "Language" ON "Group"."DefaultLanguageId" = "Language"."Id"
        WHERE "Group"."Id" = ?
        """;

    private static final String BLOCKED_USERS = """
        SELECT "BlockedUserId" as blocked_user_id, "BlockedByUserId" as blocked_by_user_id
            FROM "BlockedUser"
        WHERE "BlockedUser"."BlockedUserId" = ?
            OR "BlockedUser"."BlockedByUserId" = ?
        """;

    private static final String FOLLOW_INFO = """
        select "FollowingId", "IsMutual" from "Follower"
            where "FollowerId" = :groupId
        """;

    private static final String TEST_USERS = """
        select "Id" from "Group" where "NickName" like '1test%' order by "CreatedTime";
        """;

    public UserInfo getUserInfo(long groupId) {
        try (var mc = mainDataSource.getConnection(); var ps = mc.prepareStatement(USER_INFO)) {
            ps.setLong(1, groupId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("group_id");
                    long user_id = rs.getLong("user_id");
                    String country = rs.getString("country");
                    String language = rs.getString("language");
                    return new UserInfo(id, user_id, country, language);
                }
                return null;
            }
        } catch (SQLException e) {
            Log.warnf(e, "Failed to get UserInfo with GroupId: %s", groupId);
            return null;
        }
    }

    public List<Long> getBlockedUsers(long groupId) {
        var result = new HashSet<Long>();
        try (var mc = mainDataSource.getConnection(); var ps = mc.prepareStatement(BLOCKED_USERS)) {
            ps.setLong(1, groupId);
            ps.setLong(2, groupId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    long blockedUserId = rs.getLong("blocked_user_id");
                    long blockedByUserId = rs.getLong("blocked_by_user_id");
                    result.add(blockedUserId);
                    result.add(blockedByUserId);
                }
            }
            result.remove(groupId);
            return List.copyOf(result);
        } catch (SQLException e) {
            Log.warnf(e, "Failed to get BlockedUsers with GroupId: %s", groupId);
            return Collections.emptyList();
        }
    }

    public Map<Long, Boolean> getFollowInfo(long groupId) {
        return jdbi.withHandle(handle -> handle.createQuery(FOLLOW_INFO)
            .bind("groupId", groupId)
            .setMapKeyColumn("FollowingId")
            .setMapValueColumn("IsMutual")
            .collectInto(new GenericType<Map<Long, Boolean>>() {
            }));
    }

    @Scheduled(every = "8h", delay = 3)
    public void updateExcludedGroupIds() {
        Set<Long> testUsers = jdbi.withHandle(handle -> handle.createQuery(TEST_USERS).mapTo(Long.class).set());
        excludedGroupIds.addAll(testUsers);
    }
}
