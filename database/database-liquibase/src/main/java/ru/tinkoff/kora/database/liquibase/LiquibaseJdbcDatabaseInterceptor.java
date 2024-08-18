package ru.tinkoff.kora.database.liquibase;


import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;


public class LiquibaseJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDatabase> {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseJdbcDatabaseInterceptor.class);

    @Override
    public JdbcDatabase init(JdbcDatabase value) {
        final long started = TimeUtils.started();
        logger.debug("Liquibase migration applying...");

        DataSource dataSource = value.value();

        try (Connection connection = dataSource.getConnection()) {
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            try (Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
            logger.info("Liquibase migration applied in {}", TimeUtils.tookForLogging(started));
        } catch (LiquibaseException | SQLException e) {
            logger.error("Error during Liquibase migration", e);
            throw new RuntimeException("Liquibase migration failed", e);
        }

        return value;
    }

    @Override
    public JdbcDatabase release(JdbcDatabase value) {
        return value;
    }
}
