package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;

import java.util.function.Consumer;
import java.util.function.Function;

public interface R2dbcConnectionFactory {
    Mono<Connection> currentConnection();

    Mono<Connection> newConnection();

    DataBaseTelemetry telemetry();

    <T> Mono<T> inTx(Function<Connection, Mono<T>> callback);

    <T> Mono<T> withConnection(Function<Connection, Mono<T>> callback);

    <T> Flux<T> withConnectionFlux(Function<Connection, Flux<T>> callback);


    default <T> Mono<T> query(QueryContext queryContext, Consumer<Statement> statementSetter, Function<Flux<Result>, Mono<T>> resultFluxConsumer) {
        return Mono.deferContextual(ctx -> {
            var telemetry = this.telemetry().createContext(Context.Reactor.current(ctx), queryContext);
            return this.withConnection(connection -> {
                var stmt = connection.createStatement(queryContext.sql());
                statementSetter.accept(stmt);
                return resultFluxConsumer.apply(Flux.from(stmt.execute()));
            }).doOnEach(s -> {
                if (s.isOnComplete()) {
                    telemetry.close(null);
                } else if (s.isOnError()) {
                    telemetry.close(s.getThrowable());
                }
            });
        });
    }
}
