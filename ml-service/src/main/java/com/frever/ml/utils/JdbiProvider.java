package com.frever.ml.utils;

import com.frever.ml.comfy.ComfyUiAutoscalingCheck;
import com.frever.ml.comfy.ComfyUiTask;
import com.frever.ml.comfy.dto.ComfyUiTaskDurationItem;
import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.LikedAccount;
import com.frever.ml.dto.VideoIdAndDistance;
import com.frever.ml.dto.VideoInfo;
import com.frever.ml.follow.recsys.SecondHopFriends;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

@ApplicationScoped
public class JdbiProvider {
    @Inject
    AgroalDataSource ds;
    @Inject
    @io.quarkus.agroal.DataSource("ml")
    protected AgroalDataSource mlDataSource;

    @Singleton
    @Produces
    @Named("jdbi")
    public Jdbi jdbi() {
        return create(ds);
    }

    @Singleton
    @Produces
    @Named("ml-jdbi")
    public Jdbi mlJdbi() {
        return create(mlDataSource);
    }

    private static Jdbi create(DataSource ds) {
        Jdbi jdbi = Jdbi.create(ds);
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.registerRowMapper(ConstructorMapper.factory(GeoCluster.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(VideoInfo.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(LikedAccount.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(SecondHopFriends.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(VideoIdAndDistance.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(ComfyUiTaskDurationItem.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(ComfyUiAutoscalingCheck.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(ComfyUiTask.class));
        // jdbi.setSqlLogger(new Slf4JSqlLogger());
        return jdbi;
    }
}
