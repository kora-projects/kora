package io.koraframework.database.liquibase;

import io.koraframework.application.graph.GraphInterceptor;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.database.jdbc.JdbcDataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;


public class LiquibaseJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDataSource> {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseJdbcDatabaseInterceptor.class);

    private final LiquibaseConfig liquibaseConfig;

    public LiquibaseJdbcDatabaseInterceptor(LiquibaseConfig liquibaseConfig) {
        this.liquibaseConfig = liquibaseConfig;
    }

    @Override
    public JdbcDataSource afterInit(JdbcDataSource value) {
        final long started = TimeUtils.started();
        logger.debug("Liquibase migration applying...");

        DataSource dataSource = value.value();

        try (Connection connection = dataSource.getConnection()) {
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            try (Liquibase liquibase = new Liquibase(liquibaseConfig.changelog(), new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
            logger.info("Liquibase migration applied in {}", TimeUtils.tookForLogging(started));
        } catch (LiquibaseException | SQLException e) {
            logger.error("Error during Liquibase migration", e);
            throw new IllegalStateException("Liquibase migration failed", e);
        }

        return value;
    }

    @Override
    public JdbcDataSource beforeRelease(JdbcDataSource value) {
        return value;
    }
}
