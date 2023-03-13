package ru.tinkoff.kora.database.r2dbc;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class R2dbcDatabase implements R2dbcConnectionFactory, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(R2dbcDatabase.class);

    private static final Option<Map<String, String>> OPTIONS = Option.valueOf("options");

    private final Context.Key<Connection> connectionKey = new Context.Key<>() {
        @Override
        protected Connection copy(Connection object) {
            return null;
        }
    };
    private final Context.Key<Connection> transactionKey = new Context.Key<>() {
        @Override
        protected Connection copy(Connection object) {
            return null;
        }
    };
    private final ConnectionPool connectionFactory;
    private final DataBaseTelemetry telemetry;

    public R2dbcDatabase(R2dbcDatabaseConfig config, List<Function<ConnectionFactoryOptions.Builder, ConnectionFactoryOptions.Builder>> customizers, DataBaseTelemetryFactory telemetryFactory) {
        this.connectionFactory = r2dbcConnectionFactory(config, customizers);
        this.telemetry = telemetryFactory.get(config.poolName(), config.r2dbcUrl().substring(5, config.r2dbcUrl().indexOf(":", 6)), config.username());
    }

    @Override
    public Mono<Connection> currentConnection() {
        return Mono.deferContextual(reactorContext -> {
            var ctx = Context.Reactor.current(reactorContext);
            var connection = ctx.get(this.connectionKey);
            if (connection != null) {
                return Mono.just(connection);
            }
            return this.connectionFactory.create();
        });
    }

    @Override
    public Mono<Connection> newConnection() {
        return this.connectionFactory.create();
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Override
    public <T> Mono<T> inTx(Function<Connection, Mono<T>> callback) {
        return Mono.deferContextual(reactorContext -> {
            var ctx = Context.Reactor.current(reactorContext);
            var tx = ctx.get(this.transactionKey);
            if (tx != null) {
                return callback.apply(tx);
            }
            {
                var connection = ctx.get(this.connectionKey);
                if (connection != null) {
                    return Mono.usingWhen(
                        Mono.from(connection.beginTransaction()).thenReturn(connection),
                        (Connection c) -> {
                            ctx.set(this.transactionKey, c);
                            return callback.apply(c)
                                .onErrorResume(e -> Mono.from(c.rollbackTransaction())
                                    .then(Mono.error(e)))
                                .flatMap(r -> Mono.from(c.commitTransaction())
                                    .then(Mono.just(r)))
                                .switchIfEmpty(Mono.from(c.commitTransaction()).then(Mono.empty()));
                        },
                        c -> Mono.fromRunnable(() -> ctx.remove(this.transactionKey))
                    );
                }
            }
            return withConnection(connection -> Mono.usingWhen(
                Mono.from(connection.beginTransaction()).thenReturn(connection),
                c -> {
                    ctx.set(this.transactionKey, c);
                    return callback.apply(c)
                        .onErrorResume(e -> Mono.from(c.rollbackTransaction())
                            .then(Mono.error(e)))
                        .flatMap(r -> Mono.from(c.commitTransaction())
                            .then(Mono.just(r)))
                        .switchIfEmpty(Mono.from(c.commitTransaction()).then(Mono.empty()));
                },
                c -> Mono.fromRunnable(() -> ctx.remove(this.transactionKey))
            ));
        });
    }

    @Override
    public <T> Mono<T> withConnection(Function<Connection, Mono<T>> callback) {
        return Mono.deferContextual(reactorContext -> {
            var ctx = Context.Reactor.current(reactorContext);
            var connection = ctx.get(this.connectionKey);
            if (connection != null) {
                return callback.apply(connection);
            }
            return Mono.usingWhen(
                this.connectionFactory.create(),
                c -> {
                    ctx.set(this.connectionKey, c);
                    return callback.apply(c);
                },
                c -> {
                    ctx.remove(this.connectionKey);
                    return Mono.fromRunnable(() -> {
                        Mono.from(c.close()).subscribe();// todo maybe we should log errors here
                    });
                }
            );
        });
    }

    @Override
    public <T> Flux<T> withConnectionFlux(Function<Connection, Flux<T>> callback) {
        return Flux.deferContextual(reactorContext -> {
            var ctx = Context.Reactor.current(reactorContext);
            var connection = ctx.get(this.connectionKey);
            if (connection != null) {
                return callback.apply(connection);
            }
            return Flux.usingWhen(
                this.connectionFactory.create(),
                c -> {
                    ctx.set(this.connectionKey, c);
                    return callback.apply(c);
                },
                c -> {
                    ctx.remove(this.connectionKey);
                    return Mono.fromRunnable(() -> {
                        Mono.from(c.close())
                            .doOnError(e -> logger.warn(e.getMessage()))
                            .subscribe();
                    });
                }
            );
        });
    }

    private static ConnectionPool r2dbcConnectionFactory(R2dbcDatabaseConfig config, List<Function<ConnectionFactoryOptions.Builder, ConnectionFactoryOptions.Builder>> customizers) {
        var connectionFactoryOptions = ConnectionFactoryOptions.parse(config.r2dbcUrl())
            .mutate()
            .option(ConnectionFactoryOptions.USER, config.username())
            .option(ConnectionFactoryOptions.PASSWORD, config.password())
            .option(ConnectionFactoryOptions.CONNECT_TIMEOUT, config.connectionTimeout());

        if (config.statementTimeout() != null) {
            connectionFactoryOptions.option(ConnectionFactoryOptions.STATEMENT_TIMEOUT, config.statementTimeout());
        }

        connectionFactoryOptions.option(OPTIONS, config.options());

        for (var customizer : customizers) {
            connectionFactoryOptions = customizer.apply(connectionFactoryOptions);
        }

        var connectionFactory = ConnectionFactories.get(connectionFactoryOptions.build());
        return new ConnectionPool(ConnectionPoolConfiguration.builder()
            .name(config.poolName())
            .maxLifeTime(config.maxLifetime())
            .maxIdleTime(config.idleTimeout())
            .maxAcquireTime(config.connectionTimeout())
            .maxCreateConnectionTime(config.connectionCreateTimeout())
            .maxSize(config.maxPoolSize())
            .acquireRetry(config.acquireRetry())
            .validationQuery("SELECT 1")
            .validationDepth(ValidationDepth.REMOTE)
            .connectionFactory(connectionFactory)
            .build());
    }

    @Override
    public Mono<Void> init() {
        return this.connectionFactory.warmup().then();
    }

    @Override
    public Mono<Void> release() {
        return this.connectionFactory.disposeLater();
    }

}
