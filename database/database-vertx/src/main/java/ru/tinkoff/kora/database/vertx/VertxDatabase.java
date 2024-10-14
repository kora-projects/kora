package ru.tinkoff.kora.database.vertx;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Transaction;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;
import ru.tinkoff.kora.netty.common.NettyChannelFactory;
import ru.tinkoff.kora.vertx.common.VertxUtil;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class VertxDatabase implements Lifecycle, Wrapped<Pool>, VertxConnectionFactory, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(VertxDatabase.class);

    private final Context.Key<SqlConnection> connectionKey = new Context.Key<>() {
        @Override
        protected SqlConnection copy(SqlConnection object) {
            return object;
        }
    };
    private final Context.Key<Transaction> transactionKey = new Context.Key<>() {
        @Override
        protected Transaction copy(Transaction object) {
            return object;
        }
    };
    private final Pool pool;
    private final DataBaseTelemetry telemetry;
    private final VertxDatabaseConfig config;

    public VertxDatabase(VertxDatabaseConfig vertxDatabaseConfig,
                         EventLoopGroup eventLoopGroup,
                         NettyChannelFactory nettyChannelFactory,
                         DataBaseTelemetryFactory telemetryFactory) {
        this.config = vertxDatabaseConfig;
        this.pool = Pool.pool(
            VertxUtil.customEventLoopVertx(eventLoopGroup, nettyChannelFactory),
            VertxDatabaseConfig.toPgConnectOptions(vertxDatabaseConfig),
            VertxDatabaseConfig.toPgPoolOptions(vertxDatabaseConfig)
        );
        this.telemetry = Objects.requireNonNullElse(
            telemetryFactory.get(vertxDatabaseConfig.telemetry(), vertxDatabaseConfig.poolName(), "vertx", "postgres", vertxDatabaseConfig.username()),
            DataBaseTelemetryFactory.EMPTY
        );
    }

    @Override
    public SqlConnection currentConnection() {
        var ctx = Context.current();
        return ctx.get(this.connectionKey);
    }

    @Override
    public CompletionStage<SqlConnection> newConnection() {
        return this.pool.getConnection().toCompletionStage();
    }

    @Override
    public Pool pool() {
        return this.pool;
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Override
    public <T> CompletionStage<T> withConnection(Function<SqlConnection, CompletionStage<T>> callback) {
        var ctx = Context.current();
        var currentConnection = ctx.get(this.connectionKey);
        if (currentConnection != null) {
            return callback.apply(currentConnection);
        }

        return this.pool.withConnection(connection -> {
            ctx.set(this.connectionKey, connection);
            var f = Promise.<T>promise();
            var old = Context.current();
            try {
                ctx.inject();
                callback.apply(connection).whenComplete((result, error) -> {
                    var old1 = Context.current();
                    try {
                        ctx.inject();
                        if (error != null) {
                            f.fail(error);
                        } else {
                            f.complete(result);
                        }
                    } finally {
                        old1.inject();
                    }
                });
            } finally {
                old.inject();
            }
            return f.future();
        }).toCompletionStage();
    }

    @Override
    public <T> CompletionStage<T> inTx(Function<SqlConnection, CompletionStage<T>> callback) {
        var ctx = Context.current();
        return this.withConnection(connection -> {
            var currentTransaction = ctx.get(this.transactionKey);
            if (currentTransaction != null) {
                return callback.apply(connection);
            }
            var future = new CompletableFuture<T>();
            connection.begin(txEvent -> {
                ctx.inject();
                if (txEvent.failed()) {
                    future.completeExceptionally(txEvent.cause());
                    return;
                }
                var tx = txEvent.result();
                ctx.set(this.transactionKey, tx);
                callback.apply(connection).whenComplete((result, error) -> {
                    if (error != null) {
                        tx.rollback(v -> {
                            var oldCtx = Context.current();
                            try {
                                ctx.inject();
                                if (v.failed()) {
                                    error.addSuppressed(v.cause());
                                }
                                future.completeExceptionally(error);
                            } finally {
                                ctx.remove(this.transactionKey);
                                oldCtx.inject();
                            }
                        });
                    } else {
                        tx.commit(v -> {
                            var oldCtx1 = Context.current();
                            try {
                                ctx.inject();
                                if (v.succeeded()) {
                                    future.complete(result);
                                } else {
                                    future.completeExceptionally(v.cause());
                                }
                            } finally {
                                ctx.remove(this.transactionKey);
                                oldCtx1.inject();
                            }
                        });
                    }
                });
            });
            return future;
        });
    }

    @Override
    public void init() throws Exception {
        if (this.config.initializationFailTimeout() != null) {
            logger.debug("VertxDatabase pool '{}' starting...", config.poolName());
            var started = System.nanoTime();

            this.pool.query("SELECT 1").execute().toCompletionStage().toCompletableFuture().get(this.config.initializationFailTimeout().toMillis(), TimeUnit.MILLISECONDS);

            logger.info("VertxDatabase pool '{}' started in {}", config.poolName(), TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public void release() {
        logger.debug("VertxDatabase pool '{}' stopping...", config.poolName());
        var started = System.nanoTime();

        this.pool.close().toCompletionStage().toCompletableFuture().join();

        logger.info("VertxDatabase pool '{}' stopped in {}", config.poolName(), TimeUtils.tookForLogging(started));
    }

    @Override
    public Pool value() {
        return this.pool;
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() throws Exception {
        if (this.config.readinessProbe()) {
            this.pool.query("SELECT 1").execute().toCompletionStage().toCompletableFuture().get();
        }
        return null;
    }
}
