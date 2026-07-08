package io.koraframework.database.flyway;

import io.koraframework.application.graph.GraphInterceptor;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.database.jdbc.JdbcDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDataSource> {

    private static final Logger logger = LoggerFactory.getLogger(FlywayJdbcDatabaseInterceptor.class);

    private final FlywayConfig flywayConfig;

    public FlywayJdbcDatabaseInterceptor(FlywayConfig flywayConfig) {
        this.flywayConfig = flywayConfig;
    }

    @Override
    public JdbcDataSource afterInit(JdbcDataSource value) {
        if (flywayConfig.enabled()) {
            final long started = TimeUtils.started();
            logger.debug("FlyWay migration applying...");

            var flyway = Flyway.configure()
                .dataSource(value.value())
                .locations(flywayConfig.locations().toArray(String[]::new))
                .mixed(flywayConfig.mixed())
                .executeInTransaction(flywayConfig.executeInTransaction())
                .validateOnMigrate(flywayConfig.validateOnMigrate())
                .configuration(flywayConfig.configurationProperties())
                .loggers("slf4j")
                .load();

            switch (flywayConfig.mode()) {
                case MIGRATE -> flyway.migrate();
                case REPAIR -> flyway.repair();
                case CLEAN_MIGRATE -> {
                    flyway.clean();
                    flyway.migrate();
                }
            }

            logger.info("FlyWay migration in mode '{}' applied in {}", flywayConfig.mode(), TimeUtils.tookForLogging(started));
        } else {
            logger.info("FlyWay is disabled, skipping migrate...");
        }

        return value;
    }

    @Override
    public JdbcDataSource beforeRelease(JdbcDataSource value) {
        return value;
    }
}
