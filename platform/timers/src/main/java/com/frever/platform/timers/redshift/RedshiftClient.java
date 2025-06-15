package com.frever.platform.timers.redshift;

import static com.frever.platform.timers.utils.Utils.inIxiaProd;
import static com.frever.platform.timers.utils.Utils.notInProd;

import com.frever.platform.timers.messaging.DelayMessageHandlingException;
import com.frever.platform.timers.messaging.GroupFollowedMessage;
import com.frever.platform.timers.messaging.GroupUnfollowedMessage;
import com.frever.platform.timers.messaging.OutfitChangedMessage;
import com.google.common.base.Throwables;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Objects;

@ApplicationScoped
public class RedshiftClient {
    @Inject
    @DataSource("redshift")
    AgroalDataSource redshiftDataSource;

    private static final String INSERT_FOLLOWERS = """
        INSERT INTO events.followers (follower_id, following_id, is_mutual, interaction_time, interaction_type) VALUES (?, ?, ?, ?, ?)
        """;

    private static final String INSERT_OUTFIT = """
        INSERT INTO events.outfit (id, group_id, readiness_id, is_deleted, created_time, modified_time, files_info, sort_order, save_method, tags, name, operation)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    public void handleGroupFollowedMessage(GroupFollowedMessage groupFollowedMessage) {
        if (notInProd()) {
            Log.infof(
                "In Profile: %s, ignore GroupFollowedMessage, followerId: %s, followingId: %s.",
                ConfigUtils.getProfiles(),
                groupFollowedMessage.followerId(),
                groupFollowedMessage.followingId()
            );
            return;
        }
        if (eventsSchemaNotExists()) return;
        try (var conn = redshiftDataSource.getConnection();
             var ps = conn.prepareStatement(INSERT_FOLLOWERS)) {
            ps.setLong(1, groupFollowedMessage.followerId());
            ps.setLong(2, groupFollowedMessage.followingId());
            ps.setBoolean(3, groupFollowedMessage.isMutual());
            ps.setTimestamp(4, new Timestamp(groupFollowedMessage.time().toEpochMilli()));
            ps.setString(5, "follow");
            ps.executeUpdate();
        } catch (Exception e) {
            Log.warnf(e, "Failed to insert group followed message: %s", groupFollowedMessage);
            if (Throwables.getRootCause(e) instanceof SQLException) {
                Log.info("Got SQLException, retrying in 30 seconds...");
                throw new DelayMessageHandlingException(30);
            }
        }
    }

    public void handleGroupUnfollowedMessage(GroupUnfollowedMessage groupUnfollowedMessage) {
        if (notInProd()) {
            Log.infof(
                "In Profile: %s, ignore GroupUnfollowedMessage, followerId: %s, followingId: %s.",
                ConfigUtils.getProfiles(),
                groupUnfollowedMessage.followerId(),
                groupUnfollowedMessage.followingId()
            );
            return;
        }
        if (eventsSchemaNotExists()) return;
        try (var conn = redshiftDataSource.getConnection(); var ps = conn.prepareStatement(INSERT_FOLLOWERS)) {
            ps.setLong(1, groupUnfollowedMessage.followerId());
            ps.setLong(2, groupUnfollowedMessage.followingId());
            ps.setBoolean(3, groupUnfollowedMessage.isMutual());
            var unfollowedTime =
                Objects.requireNonNullElse(groupUnfollowedMessage.unfollowedTime(), groupUnfollowedMessage.time());
            ps.setTimestamp(4, new Timestamp(unfollowedTime.toEpochMilli()));
            ps.setString(5, "unfollow");
            ps.executeUpdate();
        } catch (Exception e) {
            Log.warnf(e, "Failed to insert group unfollowed message: %s", groupUnfollowedMessage);
            if (Throwables.getRootCause(e) instanceof SQLException) {
                Log.info("Got SQLException, retrying in 30 seconds...");
                throw new DelayMessageHandlingException(30);
            }
        }
    }

    private boolean eventsSchemaNotExists() {
        if (inIxiaProd()) {
            try (var conn = redshiftDataSource.getConnection();
                 var st = conn.createStatement()) {
                var resultSet = st.executeQuery("select 1 from SVV_ALL_SCHEMAS where schema_name = 'events'");
                if (!resultSet.next()) {
                    Log.infof("No events schema found...");
                    return true;
                }
                return false;
            } catch (Exception e) {
                Log.warnf(e, "Failed to check events schema.");
                return true;
            }
        }
        return true;
    }

    public void handleOutfitChangedMessage(OutfitChangedMessage outfitChangedMessage) {
        if (notInProd()) {
            Log.infof(
                "In Profile: %s, ignore OutfitChangedMessage, outfit ID: %s.",
                ConfigUtils.getProfiles(),
                outfitChangedMessage.id()
            );
            return;
        }
        try (var conn = redshiftDataSource.getConnection(); var ps = conn.prepareStatement(INSERT_OUTFIT)) {
            ps.setLong(1, outfitChangedMessage.id());
            ps.setLong(2, outfitChangedMessage.groupId());
            ps.setLong(3, outfitChangedMessage.readinessId());
            ps.setBoolean(4, outfitChangedMessage.isDeleted());
            ps.setTimestamp(5, new Timestamp(outfitChangedMessage.createdTime().toEpochMilli()));
            ps.setTimestamp(6, new Timestamp(outfitChangedMessage.modifiedTime().toEpochMilli()));
            ps.setString(7, outfitChangedMessage.filesInfo());
            ps.setInt(8, outfitChangedMessage.sortOrder());
            ps.setString(9, outfitChangedMessage.saveMethod());
            ps.setString(10, Arrays.toString(outfitChangedMessage.tags()));
            ps.setString(11, outfitChangedMessage.name());
            ps.setString(12, outfitChangedMessage.operation());
            ps.executeUpdate();
        } catch (Exception e) {
            Log.warnf(
                e,
                "Failed to insert outfit changed message: %s, %s, %s",
                outfitChangedMessage.id(),
                outfitChangedMessage.createdTime(),
                outfitChangedMessage.modifiedTime()
            );
            if (Throwables.getRootCause(e) instanceof SQLException) {
                Log.info("Got SQLException, retrying in 30 seconds...");
                throw new DelayMessageHandlingException(30);
            }
        }
    }
}
