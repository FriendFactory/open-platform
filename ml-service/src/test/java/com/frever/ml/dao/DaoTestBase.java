package com.frever.ml.dao;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jdbi.v3.core.Jdbi;

public abstract class DaoTestBase {
    protected static final String SERVER_IP = "3.140.110.115";
    
    @Inject
    protected AgroalDataSource mainDataSource;
    @Inject
    @Named("jdbi")
    protected Jdbi jdbi;
    @Inject
    @Named("ml-jdbi")
    protected Jdbi mlJdbi;
}
