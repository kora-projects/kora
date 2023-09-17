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

    <T> CompletionStage<T> withConnection(Function<SqlConnection, CompletionStage<T>> callback);

    <T> CompletionStage<T> inTx(Function<SqlConnection, CompletionStage<T>> callback);
}
