package ru.tinkoff.kora.database.flyway;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;

import java.time.Duration;

public final class FlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDatabase> {

    private static final Logger logger = LoggerFactory.getLogger(FlywayJdbcDatabaseInterceptor.class);

    @Override
    public Mono<JdbcDatabase> init(JdbcDatabase value) {
        return ReactorUtils
            .ioMono(() -> {
                final long started = System.nanoTime();
                logger.info("FlyWay migration starting...");

                Flyway.configure()
                    .dataSource(value.value())
                    .load()
                    .migrate();

                logger.info("FlyWay migration finished in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
            })
            .thenReturn(value);
    }

    @Override
    public Mono<JdbcDatabase> release(JdbcDatabase value) {
        return Mono.just(value);
    }
}
