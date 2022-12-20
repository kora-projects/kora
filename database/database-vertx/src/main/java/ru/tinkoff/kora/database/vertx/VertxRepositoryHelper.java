package ru.tinkoff.kora.database.vertx;

import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class VertxRepositoryHelper {
    public static <T> Mono<T> mono(VertxConnectionFactory connectionFactory, QueryContext query, Tuple params, VertxRowSetMapper<T> mapper) {
        Function<SqlConnection, Mono<T>> connectionCallback = connection -> Mono.create(sink -> {
            var telemetry = connectionFactory.telemetry().createContext(Context.Reactor.current(sink.contextView()), query);
            connection.preparedQuery(query.sql()).execute(params, rowSetEvent -> {
                if (rowSetEvent.failed()) {
                    telemetry.close(rowSetEvent.cause());
                    sink.error(rowSetEvent.cause());
                    return;
                }
                var rowSet = rowSetEvent.result();
                var result = mapper.apply(rowSet);
                telemetry.close(null);
                sink.success(result);
            });
        });

        return connectionFactory.currentConnection().map(Optional::of).defaultIfEmpty(Optional.empty()).flatMap(o -> {
            if (o.isPresent()) {
                return connectionCallback.apply(o.get());
            }
            return Mono.defer(() -> Mono.usingWhen(connectionFactory.newConnection(), connectionCallback, $connection -> Mono.fromRunnable($connection::close)));
        });
    }

    public static Mono<Void> batch(VertxConnectionFactory connectionFactory, QueryContext query, List<Tuple> params) {
        Function<SqlConnection, Mono<Void>> connectionCallback = connection -> Mono.create(sink -> {
            var telemetry = connectionFactory.telemetry().createContext(Context.Reactor.current(sink.contextView()), query);
            connection.preparedQuery(query.sql()).executeBatch(params, rowSetEvent -> {
                if (rowSetEvent.failed()) {
                    telemetry.close(rowSetEvent.cause());
                    sink.error(rowSetEvent.cause());
                    return;
                }
                telemetry.close(null);
                sink.success();
            });
        });

        return connectionFactory.currentConnection().map(Optional::of).defaultIfEmpty(Optional.empty()).flatMap(o -> {
            if (o.isPresent()) {
                return connectionCallback.apply(o.get());
            }
            return Mono.defer(() -> Mono.usingWhen(connectionFactory.newConnection(), connectionCallback, $connection -> Mono.fromRunnable($connection::close)));
        });
    }

    public static <T> Flux<T> flux(VertxConnectionFactory connectionFactory, QueryContext query, Tuple params, VertxRowMapper<T> mapper) {
        Function<SqlConnection, Flux<T>> $connectionCallback = connection -> Flux.create(sink -> {
            var telemetry = connectionFactory.telemetry().createContext(Context.Reactor.current(sink.contextView()), query);
            connection.prepare(query.sql(), statementEvent -> {
                if (statementEvent.failed()) {
                    telemetry.close(statementEvent.cause());
                    sink.error(statementEvent.cause());
                    return;
                }
                var stmt = statementEvent.result();
                var stream = stmt.createStream(50, params).pause();
                sink.onDispose(stream::close);
                sink.onRequest(stream::fetch);
                stream.exceptionHandler(e -> {
                    stmt.close();
                    sink.error(e);
                });
                stream.endHandler(v -> {
                    stmt.close();
                    sink.complete();
                });
                stream.handler(row -> {
                    var mappedRow = mapper.apply(row);
                    sink.next(mappedRow);
                });
            });
        });

        return connectionFactory.currentConnection().map(Optional::of).defaultIfEmpty(Optional.empty()).flatMapMany(o -> {
            if (o.isPresent()) {
                return $connectionCallback.apply(o.get());
            }
            return Flux.defer(() -> Flux.usingWhen(connectionFactory.newConnection(), $connectionCallback, $connection -> Mono.fromRunnable($connection::close)));
        });
    }
}
