package ru.tinkoff.kora.database.flyway;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;

import java.time.Duration;

public final class FlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDatabase> {

    private static final Logger logger = LoggerFactory.getLogger(FlywayJdbcDatabaseInterceptor.class);

    @Override
    public JdbcDatabase init(JdbcDatabase value) {
        final long started = System.nanoTime();
        logger.info("FlyWay migration starting...");

        Flyway.configure()
            .dataSource(value.value())
            .load()
            .migrate();

        logger.info("FlyWay migration finished in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        return value;
    }

    @Override
    public JdbcDatabase release(JdbcDatabase value) {
        return value;
    }
}
