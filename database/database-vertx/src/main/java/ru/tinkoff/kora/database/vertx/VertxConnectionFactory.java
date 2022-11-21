package ru.tinkoff.kora.database.vertx;

import io.vertx.sqlclient.SqlConnection;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;

import java.util.function.Function;

public interface VertxConnectionFactory {
    Mono<SqlConnection> currentConnection();

    Mono<SqlConnection> newConnection();

    DataBaseTelemetry telemetry();

    <T> Mono<T> withConnection(Function<SqlConnection, Mono<T>> callback);

    <T> Mono<T> inTx(Function<SqlConnection, Mono<T>> callback);
}
