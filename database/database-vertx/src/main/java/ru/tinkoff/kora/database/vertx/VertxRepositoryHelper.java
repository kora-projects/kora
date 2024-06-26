package ru.tinkoff.kora.database.vertx;

import io.vertx.sqlclient.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class VertxRepositoryHelper {

    private VertxRepositoryHelper() {}

    public static <T> CompletableFuture<T> completionStage(VertxConnectionFactory connectionFactory, QueryContext query, Tuple params, VertxRowSetMapper<T> mapper) {
        var connection = connectionFactory.currentConnection();
        if (connection != null) {
            return completionStage(connection, connectionFactory.telemetry(), query, params, mapper);
        }
        return connectionFactory.newConnection().toCompletableFuture()
            .thenCompose(c -> completionStage(c, connectionFactory.telemetry(), query, params, mapper)
            .whenComplete((t, throwable) -> c.close()));
    }

    public static <T> CompletableFuture<T> completionStage(SqlClient connection, DataBaseTelemetry dataBaseTelemetry, QueryContext query, Tuple params, VertxRowSetMapper<T> mapper) {
        var ctxCurrent = Context.current();
        var ctxFork = ctxCurrent.fork();
        var telemetry = dataBaseTelemetry.createContext(ctxFork, query);
        var future = new CompletableFuture<T>();
        connection.preparedQuery(query.sql()).execute(params, rowSetEvent -> {
            ctxFork.inject();
            if (rowSetEvent.failed()) {
                telemetry.close(rowSetEvent.cause());
                future.completeExceptionally(rowSetEvent.cause());
                return;
            }
            T result;
            try {
                var rowSet = rowSetEvent.result();
                result = mapper.apply(rowSet);
            } catch (Exception e) {
                telemetry.close(e);
                ctxCurrent.inject();
                future.completeExceptionally(e);
                return;
            }
            telemetry.close(null);
            ctxCurrent.inject();
            future.complete(result);
        });
        return future;
    }

    public static CompletableFuture<UpdateCount> batchCompletionStage(VertxConnectionFactory connectionFactory, QueryContext query, List<Tuple> params) {
        var connection = connectionFactory.currentConnection();
        if (connection != null) {
            return batchCompletionStage(connection, connectionFactory.telemetry(), query, params);
        }
        return connectionFactory.newConnection().toCompletableFuture().thenCompose(c -> batchCompletionStage(c, connectionFactory.telemetry(), query, params)
            .whenComplete((t, throwable) -> c.close()));

    }

    public static CompletableFuture<UpdateCount> batchCompletionStage(SqlClient connection, DataBaseTelemetry dataBaseTelemetry, QueryContext query, List<Tuple> params) {
        var ctxCurrent = Context.current();
        var ctxFork = ctxCurrent.fork();
        var telemetry = dataBaseTelemetry.createContext(ctxFork, query);
        var future = new CompletableFuture<UpdateCount>();
        connection.preparedQuery(query.sql()).executeBatch(params, rowSetEvent -> {
            ctxFork.inject();
            if (rowSetEvent.failed()) {
                telemetry.close(rowSetEvent.cause());
                future.completeExceptionally(rowSetEvent.cause());
                return;
            }
            int result = 0;
            try {
                var rowSet = rowSetEvent.result();
                while (rowSet != null) {
                    result += rowSet.rowCount();
                    rowSet = rowSet.next();
                }
            } catch (Exception e) {
                telemetry.close(e);
                ctxCurrent.inject();
                future.completeExceptionally(e);
                return;
            }
            telemetry.close(null);
            ctxCurrent.inject();
            future.complete(new UpdateCount(result));
        });
        return future;
    }

    public static class Reactor {
        private Reactor() {}

        public static <T> Mono<T> mono(VertxConnectionFactory connectionFactory, QueryContext query, Tuple params, VertxRowSetMapper<T> mapper) {
            return Mono.defer(() -> {
                var connection = connectionFactory.currentConnection();
                var telemetry = connectionFactory.telemetry();
                if (connection != null) {
                    return mono(connection, telemetry, query, params, mapper);
                }
                return Mono.usingWhen(Mono.fromCompletionStage(connectionFactory.newConnection()), c -> mono(c, telemetry, query, params, mapper), $connection -> Mono.fromRunnable($connection::close));
            });
        }

        public static <T> Mono<T> mono(SqlClient connection, DataBaseTelemetry dataBaseTelemetry, QueryContext query, Tuple params, VertxRowSetMapper<T> mapper) {
            return Mono.create(sink -> {
                var ctxCurrent = Context.Reactor.current(sink.contextView());
                var ctxFork = ctxCurrent.fork();
                var telemetry = dataBaseTelemetry.createContext(ctxFork, query);
                connection.preparedQuery(query.sql()).execute(params, rowSetEvent -> {
                    ctxFork.inject();
                    if (rowSetEvent.failed()) {
                        telemetry.close(rowSetEvent.cause());
                        sink.error(rowSetEvent.cause());
                        return;
                    }
                    try {
                        var rowSet = rowSetEvent.result();
                        var result = mapper.apply(rowSet);
                        telemetry.close(null);
                        sink.success(result);
                        ctxCurrent.inject();
                    } catch (Exception e) {
                        telemetry.close(e);
                        sink.error(e);
                        ctxCurrent.inject();
                    }
                });
            });
        }

        public static Mono<UpdateCount> batchMono(VertxConnectionFactory connectionFactory, QueryContext query, List<Tuple> params) {
            return Mono.defer(() -> {
                var connection = connectionFactory.currentConnection();
                if (connection != null) {
                    return batchMono(connection, connectionFactory.telemetry(), query, params);
                }
                return Mono.usingWhen(Mono.fromCompletionStage(connectionFactory.newConnection()), c -> batchMono(c, connectionFactory.telemetry(), query, params), $connection -> Mono.fromRunnable($connection::close));
            });
        }

        public static Mono<UpdateCount> batchMono(SqlClient connection, DataBaseTelemetry dataBaseTelemetry, QueryContext query, List<Tuple> params) {
            return Mono.create(sink -> {
                var ctxCurrent = Context.Reactor.current(sink.contextView());
                var ctxFork = ctxCurrent.fork();
                var telemetry = dataBaseTelemetry.createContext(ctxFork, query);
                connection.preparedQuery(query.sql()).executeBatch(params, rowSetEvent -> {
                    ctxFork.inject();
                    if (rowSetEvent.failed()) {
                        telemetry.close(rowSetEvent.cause());
                        sink.error(rowSetEvent.cause());
                        return;
                    }

                    long counter = 0;
                    try {
                        RowSet<Row> current = rowSetEvent.result();
                        while (current != null) {
                            counter += current.rowCount();
                            current = current.next();
                        }
                    } catch (Exception e) {
                        telemetry.close(e);
                        sink.error(e);
                        ctxCurrent.inject();
                        return;
                    }

                    telemetry.close(null);
                    sink.success(new UpdateCount(counter));
                    ctxCurrent.inject();
                });
            });
        }

        public static <T> Flux<T> flux(VertxConnectionFactory connectionFactory, QueryContext query, Tuple params, VertxRowMapper<T> mapper) {
            return Flux.deferContextual(contextView -> {
                var connection = connectionFactory.currentConnection();
                if (connection != null) {
                    return flux(connection, connectionFactory.telemetry(), query, params, mapper);
                }
                return Flux.usingWhen(Mono.fromCompletionStage(connectionFactory.newConnection()), c -> flux(c, connectionFactory.telemetry(), query, params, mapper), $connection -> Mono.fromRunnable($connection::close));
            });
        }

        public static <T> Flux<T> flux(SqlConnection connection, DataBaseTelemetry dataBaseTelemetry, QueryContext query, Tuple params, VertxRowMapper<T> mapper) {
            return Flux.create(sink -> {
                var ctxCurrent = Context.Reactor.current(sink.contextView());
                var ctxFork = ctxCurrent.fork();
                var telemetry = dataBaseTelemetry.createContext(ctxFork, query);
                connection.prepare(query.sql(), statementEvent -> {
                    ctxFork.inject();
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
                        telemetry.close(e);
                        sink.error(e);
                        ctxCurrent.inject();
                    });
                    stream.endHandler(v -> {
                        stmt.close();
                        telemetry.close(null);
                        sink.complete();
                        ctxCurrent.inject();
                    });
                    stream.handler(row -> {
                        var mappedRow = mapper.apply(row);
                        sink.next(mappedRow);
                    });
                });
            });
        }
    }
}
