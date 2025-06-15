package com.frever.ml.dao;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jdbi.v3.core.Jdbi;

public abstract class BaseDao {
    @Inject
    protected AgroalDataSource mainDataSource;
    @Inject
    @Named("jdbi")
    protected Jdbi jdbi;
    @Inject
    @Named("ml-jdbi")
    protected Jdbi mlJdbi;
}
