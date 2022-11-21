package ru.tinkoff.kora.database.vertx.pool;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.*;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;
import reactor.pool.PooledRef;
import ru.tinkoff.kora.database.vertx.VertxDatabaseConfig;
import ru.tinkoff.kora.database.vertx.pool.factory.MultipleHostsVertxSqlConnectionFactory;
import ru.tinkoff.kora.database.vertx.pool.factory.SingleHostVertxSqlConnectionFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class VertxSqlPool implements Pool {
    private final InstrumentedPool<SqlConnectionHolder> pool;
    private final VertxDatabaseConfig config;
    private final Scheduler eventLoopGroupScheduler;

    public VertxSqlPool(VertxDatabaseConfig config, EventLoopGroup eventLoopGroup) {
        this.config = config;
        this.eventLoopGroupScheduler = Schedulers.fromExecutorService(eventLoopGroup);
        this.eventLoopGroupScheduler.start();
        var factory = config.host().contains(",")
            ? new MultipleHostsVertxSqlConnectionFactory(config)
            : new SingleHostVertxSqlConnectionFactory(config);
        this.pool = PoolBuilder.from(Mono.defer(() -> factory.connect(eventLoopGroup).map(SqlConnectionHolder::new)))
            .evictInBackground(Duration.ofSeconds(10))
            .evictionIdle(Duration.ofMillis(config.idleTimeout()))
            .acquisitionScheduler(this.eventLoopGroupScheduler)
            .sizeBetween(0, config.maxPoolSize())
            .destroyHandler(h -> Mono.create(sink -> h.con.close(event -> {
                if (event.succeeded()) {
                    sink.success();
                } else {
                    sink.error(event.cause());
                }
            })))
            .buildPool();
    }

    public <T> void withPooledConnection(BiConsumer<PooledSqlConnection, Handler<AsyncResult<T>>> function, Handler<AsyncResult<T>> handler) {
        this.getConnection(this.config.acquireTimeout(), result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
                return;
            }
            var connection = result.result();
            function.accept(connection, event -> {
                connection.close();
                handler.handle(event);
            });
        });
    }

    public void getConnection(long timeout, Handler<AsyncResult<PooledSqlConnection>> handler) {
        var started = System.currentTimeMillis();

        this.pool.acquire(Duration.ofMillis(timeout)).subscribeWith(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(PooledRef<SqlConnectionHolder> ref) {
                var holder = ref.poolable();

                if (System.currentTimeMillis() - holder.getLastSuccessfulOperation() <= VertxSqlPool.this.config.aliveBypassWindow()) {
                    handler.handle(Future.succeededFuture(new PooledSqlConnection(ref)));
                    return;
                }
                holder.con.preparedQuery("SELECT 1").execute().onComplete(event -> {
                    if (event.succeeded()) {
                        handler.handle(Future.succeededFuture(new PooledSqlConnection(ref)));
                        return;
                    }

                    var now = System.currentTimeMillis();
                    var timeLeft = timeout - (now - started);
                    if (timeLeft < 100) {
                        handler.handle(Future.failedFuture(event.cause()));
                    } else {
                        VertxSqlPool.this.eventLoopGroupScheduler.schedule(
                            () -> VertxSqlPool.this.getConnection(timeLeft, handler),
                            100,
                            TimeUnit.MILLISECONDS
                        );
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                var now = System.currentTimeMillis();
                var timeLeft = timeout - (now - started);
                if (timeLeft < 100) {
                    handler.handle(Future.failedFuture(t));
                } else {
                    VertxSqlPool.this.eventLoopGroupScheduler.schedule(
                        () -> VertxSqlPool.this.getConnection(timeLeft, handler),
                        100,
                        TimeUnit.MILLISECONDS
                    );
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    @Override
    public void getConnection(Handler<AsyncResult<SqlConnection>> handler) {
        this.getConnection(this.config.acquireTimeout(), r -> {
            if (r.failed()) {
                handler.handle(Future.failedFuture(r.cause()));
            } else {
                handler.handle(Future.succeededFuture(r.result()));
            }
        });
    }

    @Override
    public Future<SqlConnection> getConnection() {
        return Future.future(this::getConnection);
    }

    @Override
    public Query<RowSet<Row>> query(String sql) {
        return new PooledPgPreparedQueryForPool(this, sql, null);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String sql) {
        return new PooledPgPreparedQueryForPool(this, sql, null);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String sql, PrepareOptions options) {
        return new PooledPgPreparedQueryForPool(this, sql, options);
    }

    @Override
    public void close(Handler<AsyncResult<Void>> handler) {
        this.pool.disposeLater().subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(Void unused) {}

            @Override
            public void onError(Throwable t) {
                handler.handle(Future.failedFuture(t));
            }

            @Override
            public void onComplete() {
                handler.handle(Future.succeededFuture());
            }
        });
    }

    @Override
    public Future<Void> close() {
        return Future.future(this::close);
    }

    @Override
    public Pool connectHandler(Handler<SqlConnection> handler) {
        return this;
    }

    @Override
    public Pool connectionProvider(Function<Context, Future<SqlConnection>> provider) {
        return this;
    }

    @Override
    public int size() {
        return this.pool.metrics().allocatedSize();
    }
}
