package ru.tinkoff.kora.database.flyway;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;

public final class FlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDatabase> {

    private static final Logger logger = LoggerFactory.getLogger(FlywayJdbcDatabaseInterceptor.class);

    private final FlywayConfig flywayConfig;

    public FlywayJdbcDatabaseInterceptor(FlywayConfig flywayConfig) {
        this.flywayConfig = flywayConfig;
    }

    @Override
    public JdbcDatabase init(JdbcDatabase value) {
        if (flywayConfig.enabled()) {
            final long started = TimeUtils.started();
            logger.debug("FlyWay migration applying...");

            Flyway.configure()
                .dataSource(value.value())
                .locations(flywayConfig.locations().toArray(String[]::new))
                .mixed(flywayConfig.mixed())
                .executeInTransaction(flywayConfig.executeInTransaction())
                .validateOnMigrate(flywayConfig.validateOnMigrate())
                .configuration(flywayConfig.configurationProperties())
                .loggers("slf4j")
                .load()
                .migrate();

            logger.info("FlyWay migration applied in {}", TimeUtils.tookForLogging(started));
        } else {
            logger.info("FlyWay is disabled, skipping migrate...");
        }

        return value;
    }

    @Override
    public JdbcDatabase release(JdbcDatabase value) {
        return value;
    }
}
