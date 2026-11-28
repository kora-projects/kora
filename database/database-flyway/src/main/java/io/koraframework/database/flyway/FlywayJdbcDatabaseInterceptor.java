package io.koraframework.database.flyway;

import io.koraframework.application.graph.*;
import io.koraframework.common.util.*;
import io.koraframework.database.jdbc.*;
import org.flywaydb.core.*;
import org.slf4j.*;

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

            var locationsList = flywayConfig.locations();
            final int size = locationsList.size();
            var locationsArray = new String[size];
            for (int i = 0; i < size; i++) {
                locationsArray[i] = locationsList.get(i);
            }

            var flyway = Flyway.configure()
                .dataSource(value.value())
                .locations(locationsArray)
                .mixed(flywayConfig.mixed())
                .executeInTransaction(flywayConfig.executeInTransaction())
                .validateOnMigrate(flywayConfig.validateOnMigrate())
                .configuration(flywayConfig.configurationProperties())
                .loggers("slf4j")
                .load();

            switch (flywayConfig.mode()) {
                case MIGRATE:
                    flyway.migrate();
                    break;
                case REPAIR:
                    flyway.repair();
                    break;
                case CLEAN_MIGRATE:
                    flyway.clean();
                    flyway.migrate();
                    break;
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
