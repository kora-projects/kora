package ru.tinkoff.kora.database.flyway;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;

import java.time.Duration;

public final class FlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDatabase> {

    private static final Logger logger = LoggerFactory.getLogger(FlywayJdbcDatabaseInterceptor.class);

    @Override
    public JdbcDatabase init(JdbcDatabase value) {
        final long started = TimeUtils.started();
        logger.debug("FlyWay migration applying...");

        Flyway.configure()
            .dataSource(value.value())
            .load()
            .migrate();

        logger.info("FlyWay migration applied in {}", TimeUtils.tookForLogging(started));
        return value;
    }

    @Override
    public JdbcDatabase release(JdbcDatabase value) {
        return value;
    }
}
