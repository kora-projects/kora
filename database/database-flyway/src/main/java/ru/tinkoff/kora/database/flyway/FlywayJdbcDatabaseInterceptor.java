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
                logger.info("Starting FlyWay migration...");

                Flyway.configure()
                    .dataSource(value.value())
                    .load()
                    .migrate();

                logger.info("Finished FlyWay migration took {}", Duration.ofNanos(System.nanoTime() - started));
            })
            .thenReturn(value);
    }

    @Override
    public Mono<JdbcDatabase> release(JdbcDatabase value) {
        return Mono.just(value);
    }
}
