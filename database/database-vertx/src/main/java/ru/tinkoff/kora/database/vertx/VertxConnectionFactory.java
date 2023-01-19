package ru.tinkoff.kora.database.vertx;

import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnection;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface VertxConnectionFactory {
    SqlConnection currentConnection();

    CompletionStage<SqlConnection> newConnection();

    Pool pool();

    DataBaseTelemetry telemetry();

    <T> Mono<T> withConnection(Function<SqlConnection, Mono<T>> callback);

    <T> Mono<T> inTx(Function<SqlConnection, Mono<T>> callback);
}
