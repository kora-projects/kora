package ru.tinkoff.kora.database.flyway;

import org.flywaydb.core.Flyway;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.database.jdbc.JdbcDataBase;

public final class FlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDataBase> {
    @Override
    public Mono<JdbcDataBase> init(JdbcDataBase value) {
        return ReactorUtils
            .ioMono(() -> {
                Flyway.configure()
                    .dataSource(value.value())
                    .load()
                    .migrate();
            })
            .thenReturn(value);
    }

    @Override
    public Mono<JdbcDataBase> release(JdbcDataBase value) {
        return Mono.just(value);
    }
}
