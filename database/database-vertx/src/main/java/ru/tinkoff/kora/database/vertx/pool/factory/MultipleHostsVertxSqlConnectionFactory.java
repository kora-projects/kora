package ru.tinkoff.kora.database.vertx.pool.factory;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.SqlConnection;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import ru.tinkoff.kora.database.vertx.VertxDatabaseConfig;
import ru.tinkoff.kora.vertx.common.VertxUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MultipleHostsVertxSqlConnectionFactory implements VertxSqlConnectionFactory {
    private final VertxDatabaseConfig config;
    private final List<PgHost> hosts;
    private final VertxDatabaseConfig.HostRequirement hostRequirement;

    public MultipleHostsVertxSqlConnectionFactory(VertxDatabaseConfig config) {
        this.config = config;
        this.hostRequirement = Objects.requireNonNullElse(this.config.hostRequirement(), VertxDatabaseConfig.HostRequirement.PRIMARY);
        this.hosts = Arrays.stream(config.host().split(","))
            .map(s -> {
                if (s.contains(":")) {
                    var hostAndPort = s.split(":");
                    return new PgHost(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
                }
                return new PgHost(s, 5432);
            })
            .toList();
    }

    @Override
    public Mono<SqlConnection> connect(EventLoopGroup eventLoopGroup) {
        return Mono.create(sink -> {
            var cancelled = new AtomicBoolean(false);
            var error = new AtomicReference<Throwable>(null);
            sink.onCancel(() -> cancelled.set(true));
            connect(eventLoopGroup, error, cancelled, sink, 0);
        });
    }

    private record PgHost(String host, int port) {}

    private void connect(EventLoopGroup eventLoopGroup, AtomicReference<Throwable> error, AtomicBoolean cancelled, MonoSink<SqlConnection> sink, int hostIndex) {
        if (hostIndex >= hosts.size()) {
            sink.error(error.get());
            return;
        }
        var pgHost = hosts.get(hostIndex);
        var pgOptions = config.toPgConnectOptions(pgHost.host, pgHost.port);
        connect(pgOptions, eventLoopGroup, connectionResult -> {
            if (cancelled.get()) {
                if (connectionResult.succeeded()) {
                    connectionResult.result().close();
                }
                return;
            }
            if (connectionResult.succeeded()) {
                sink.success(connectionResult.result());
                return;
            }
            var cause = connectionResult.cause();
            if (!error.compareAndSet(null, cause)) {
                error.get().addSuppressed(cause);
            }
            connect(eventLoopGroup, error, cancelled, sink, hostIndex + 1);
        });
    }

    private void connect(PgConnectOptions config, EventLoopGroup eventLoopGroup, Handler<AsyncResult<PgConnection>> handler) {
        var vertx = VertxUtil.customEventLoopVertx(eventLoopGroup);
        PgConnection.connect(vertx, config, connectionResult -> {
            if (connectionResult.failed()) {
                var cause = connectionResult.cause();
                handler.handle(Future.failedFuture(cause));
                return;
            }
            var connection = connectionResult.result();
            isPrimaryServer(connection, isPrimaryResult -> {
                if (isPrimaryResult.succeeded()) {
                    var isPrimary = isPrimaryResult.result();
                    var requirementMatches = switch (this.hostRequirement) {
                        case PRIMARY -> isPrimary;
                        case SECONDARY -> !isPrimary;
                    };
                    if (requirementMatches) {
                        handler.handle(Future.succeededFuture(connection));
                        return;
                    }
                    connection.close();
                    var host = config.getHost() + ":" + config.getPort();
                    var exception = new InvalidBackendTypeException("Host %s(primary=%s) doesn't match requirement %s".formatted(host, isPrimaryResult.result(), hostRequirement));
                    handler.handle(Future.failedFuture(exception));
                    return;
                }
                connection.close();
                var cause = connectionResult.cause();
                handler.handle(Future.failedFuture(cause));
            });
        });
    }

    private static class InvalidBackendTypeException extends RuntimeException {
        public InvalidBackendTypeException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

    }

    private static void isPrimaryServer(PgConnection connection, Handler<AsyncResult<Boolean>> handler) {
        connection.preparedQuery("SHOW TRANSACTION_READ_ONLY").execute(event -> {
            if (event.failed()) {
                handler.handle(Future.failedFuture(event.cause()));
                return;
            }
            final boolean txReadOnly;
            try {
                var rs = event.result();
                var row = rs.iterator().next();
                var txReadOnlyString = row.getString(0);
                txReadOnly = txReadOnlyString.equalsIgnoreCase("off");
            } catch (Exception e) {
                handler.handle(Future.failedFuture(e));
                return;
            }
            handler.handle(Future.succeededFuture(txReadOnly));
        });
    }

}
